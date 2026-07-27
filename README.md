# Jenkins CI/CD Lab — Automation Test Execution

REST Assured test suite (JUnit 5 + Allure) against [FakeStoreAPI](https://fakestoreapi.com), containerized and
run through a Jenkins pipeline (Docker, Blue Ocean, GitHub webhook trigger, Slack notification).

Lab brief: *Automation Test Execution with Jenkins CI/CD* (Quality Assurance Labs, AmaliTech Training Academy).
See [`test-plan.md`](./test-plan.md) for full test case documentation, including two FakeStoreAPI behaviors
that intentionally look "wrong" against normal REST semantics (documented there, not a suite defect).

---

## Architecture

```
Jenkins-lab/
├── src/                    REST Assured test suite (target: fakestoreapi.com)
├── Dockerfile              Containerizes the test suite (maven:3.9-eclipse-temurin-17)
├── test-plan.md            Test case documentation
├── jenkins/
│   ├── Dockerfile          Custom Jenkins image: jenkins/jenkins:lts-jdk17 + Maven 3.9.16 + plugins
│   └── plugins.txt         git, github, workflow-aggregator, credentials-binding, htmlpublisher, junit, blueocean
├── docker-compose.yml      Runs the Jenkins image, named volume for jenkins_home (see note below)
└── Jenkinsfile             Declarative pipeline: Checkout -> Build -> Test -> Report -> Notify
```

**Why a named Docker volume for `jenkins_home`:** this repo lives under OneDrive. A bind mount would put
Jenkins' thousands of small home-directory files inside a OneDrive-synced folder, and OneDrive fighting
Jenkins for file locks causes corruption/permission errors. `docker-compose.yml` uses a Docker-managed named
volume instead — outside OneDrive's reach.

**Why Maven runs directly instead of Docker-in-Docker:** the pipeline's job is checkout → build deps → test →
archive reports, none of which need the *pipeline itself* to build a Docker image. Maven 3.9.16 is baked
directly into the Jenkins image and put on `PATH`, so `sh 'mvn test'` just works with no Global Tool
Configuration step and no Docker socket mounted into the Jenkins container. The containerization deliverable
is still satisfied by this repo's own `Dockerfile` — build and run it once, see below.

---

## Prerequisites already done

- Docker Desktop running, `docker compose up -d` brings up Jenkins on `localhost:8080` (ports `8080`, `50000`).
- Test suite verified green both locally (`mvn test`) and inside its own container (`docker build && docker run`).
- Jenkins image built with Maven 3.9.16 + all required plugins pre-installed (no manual plugin install needed).

## Setup steps (do these in order — the order matters)

### 1. Unlock Jenkins
Open `http://localhost:8080`. Paste in the initial admin password (given to you separately), then create your
admin user through the setup wizard.

### 2. Create the Slack credential
- In Slack: add an **Incoming Webhook** app to your workspace, copy the webhook URL.
- In Jenkins: **Manage Jenkins → Credentials → (global) → Add Credentials**
  - Kind: **Secret text**
  - Secret: the Slack webhook URL
  - ID: `slack-webhook-url` (must match exactly — the Jenkinsfile references this ID)

Do this **before** step 4 — the Jenkinsfile's `environment` block resolves this credential at pipeline
*startup*. If it's missing, the very first build fails before any stage runs.

### 3. Create the pipeline job
**New Item → Pipeline** (name it e.g. `jenkins-lab`).
Under **Pipeline**, set:
- Definition: **Pipeline script from SCM** (not "Pipeline script" — the Jenkinsfile calls `checkout scm`,
  which needs an actual SCM configured on the job to resolve against)
- SCM: Git, repository URL: `https://github.com/nellybutera/Jenkins-lab.git`, branch `main`
- Script Path: `Jenkinsfile`

### 4. Run it once, manually
Click **Build Now**. This proves the pipeline end-to-end (checkout, build, test, Allure/JUnit report, Slack
notification) *and* it's what arms the `githubPush()` trigger — Jenkins only starts listening for that
trigger after the Jenkinsfile has executed at least once. Skip this and the webhook in step 5 will silently
do nothing.

Check the **Allure Report** link on the build page. Jenkins' default Content-Security-Policy can block
Allure's JS and render the page blank — if that happens, it's a known Jenkins/Allure interaction, not a
broken pipeline (the **JUnit** trend graph on the build page will show real pass/fail data regardless). Fix,
if needed, via **Manage Jenkins → Script Console**:
```groovy
System.setProperty("hudson.model.DirectoryBrowserSupport.CSP", "")
```

### 5. Wire up the GitHub webhook

**Claim a free static ngrok domain first** (so the URL never changes between restarts): ngrok dashboard →
**Universal Gateway → Domains → Create Domain**, and grab your **Authtoken** from the dashboard too.

**Run ngrok as a Docker container** on the same Docker network as Jenkins, rather than the native Windows
`ngrok.exe` — the native binary gets flagged and blocked by Windows Defender ("contains a virus or potentially
unwanted software", a known heuristic false positive for tunneling tools). Running it in Docker sidesteps that
entirely and lets it address the Jenkins container directly by name instead of `localhost`:

```bash
docker run -d --name ngrok-tunnel \
  --network jenkins-lab_default \
  -e NGROK_AUTHTOKEN=<your-authtoken> \
  ngrok/ngrok:latest http jenkins-lab-jenkins-1:8080 --url=https://<your-static-domain>.ngrok-free.dev
```

Verify the tunnel actually reaches Jenkins before touching GitHub:
```bash
curl -sI https://<your-static-domain>.ngrok-free.dev | grep X-Jenkins
```
A `X-Jenkins: <version>` header confirms the request is really landing on your Jenkins container.

**Register the webhook** on the GitHub repo: **Settings → Webhooks → Add webhook**
  - Payload URL: `https://<your-static-domain>.ngrok-free.dev/github-webhook/` — **the trailing slash is
    required**. Without it, Jenkins 302-redirects the request instead of processing it, and GitHub logs the
    delivery as a failure (`Invalid HTTP Response: 302`) rather than following the redirect. Check this exact
    mistake first if deliveries aren't triggering builds.
  - Content type: `application/json`
  - Secret: a random string (e.g. `openssl rand -hex 32`) — GitHub signs every payload with it; for Jenkins to
    actually *verify* the signature (not just accept the delivery) also configure the same value under
    **Manage Jenkins → System → GitHub → Advanced → Shared secrets**. Optional hardening, not required for the
    trigger to work.
  - Trigger: Just the push event

**Verify end-to-end:** push a commit, then check the delivery log on the webhook's **Recent Deliveries** tab
(or `gh api repos/<owner>/<repo>/hooks/<id>/deliveries`) for a `200` response, and confirm a new build appeared
under the job with `SUCCESS` and a Slack notification.

**Known limitation:** the ngrok container (and Jenkins itself) only receives webhooks while both are actually
running — restarting your machine, Docker Desktop, or `ngrok-tunnel` all require bringing everything back up
before a push will trigger anything. If you don't want to manage that for a given demo, fall back to
**Poll SCM** on the job (`* * * * *` schedule) instead of the webhook trigger — documented here as the
resilient backup, not the primary path, since the lab brief specifically calls out webhook integration as its
own deliverable.

### 6. Explore Blue Ocean
Jenkins → **Open Blue Ocean** (left nav) for the visual pipeline view — good material for the demo recording.

### 7. Demonstrate containerization
The pipeline itself doesn't build the suite's Docker image (see architecture note above), so show it
separately once:
```bash
docker build -t jenkins-lab-tests .
docker run --rm jenkins-lab-tests
```

---

## Grading deliverable mapping

| Deliverable | Where |
|---|---|
| Test suite implementation (20) | `src/`, `pom.xml`, `test-plan.md` |
| Jenkins environment setup (25) | `jenkins/Dockerfile`, `jenkins/plugins.txt`, `docker-compose.yml`, Blue Ocean |
| Pipeline functionality (40) | `Jenkinsfile` (checkout/build/test/archive), GitHub webhook (step 5) |
| Notification system (15) | Slack `post { success/failure }` block in `Jenkinsfile` |

---

## Local commands (reference)

```bash
mvn test                      # run the suite locally
mvn allure:serve              # open the Allure report in a browser
docker build -t jenkins-lab-tests .
docker run --rm jenkins-lab-tests
docker compose up -d          # start Jenkins
docker compose down           # stop Jenkins (jenkins_home volume persists)
```
