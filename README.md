# Jenkins CI/CD Lab — Automation Test Execution

REST Assured test suite (JUnit 5 + Allure) against [FakeStoreAPI](https://fakestoreapi.com), containerized and
run through a Jenkins pipeline with Blue Ocean, a live GitHub webhook trigger, and Slack notifications.

See [`test-plan.md`](./test-plan.md) for full test case documentation — including two FakeStoreAPI behaviors
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
├── docker-compose.yml      Runs the Jenkins image, named volume for jenkins_home
└── Jenkinsfile             Declarative pipeline: Checkout -> Build -> Test -> Report -> Notify
```

**Design decisions:**
- **Named Docker volume, not a bind mount, for `jenkins_home`** — this repo lives under OneDrive, and syncing
  Jenkins' thousands of internal files would cause file-lock/corruption issues.
- **Maven runs directly inside the Jenkins image, no Docker-in-Docker** — the pipeline only checks out, builds,
  and tests; it never needs to build a Docker image itself. Containerization is still demonstrated separately
  via this repo's own `Dockerfile`.
- **A real GitHub webhook, not SCM polling** — the lab explicitly grades webhook integration, so the pipeline
  triggers instantly on push instead of on a timer.

---

## Pipeline flow

Push to `main` → GitHub sends a webhook → Jenkins checks out the latest commit → builds dependencies → runs
all 11 tests → publishes the JUnit trend and Allure report → posts a Slack message with branch, commit, test
counts, duration, and direct links to the build and report.

---

## Running it

```bash
docker compose up -d          # start Jenkins (jenkins_home persists across restarts)
docker start ngrok-tunnel     # resume the public webhook tunnel
```

Jenkins UI: `https://semester-gulf-kite.ngrok-free.dev` (or `localhost:8080` on this machine).

```bash
mvn test                                                              # run the suite locally
mvn allure:serve                                                      # view the Allure report
docker build -t jenkins-lab-tests . && docker run --rm jenkins-lab-tests   # containerized run
```

Both Jenkins and the ngrok tunnel need to be running for a push to trigger a build — if either was stopped
(e.g. after restarting the machine), bring both back up with the two commands above before expecting a
webhook to fire.

---

## Notable issues solved along the way

- **Webhook payload URL needs a trailing slash** (`/github-webhook/`, not `/github-webhook`) — without it,
  Jenkins redirects instead of processing the request, and GitHub logs the delivery as failed.
- **Jenkins' Content-Security-Policy can block the Allure report from rendering** (blank page, JS blocked) —
  fixed once via **Manage Jenkins → Script Console**: `System.setProperty("hudson.model.DirectoryBrowserSupport.CSP", "")`
- **ngrok runs as a Docker container**, not the native Windows binary — the native `ngrok.exe` was flagged and
  blocked by Windows Defender (a known false positive for tunneling tools); running it in Docker on the same
  network as Jenkins avoided the issue entirely.

---

## Grading deliverable mapping

| Deliverable | Where |
|---|---|
| Test suite implementation (20) | `src/`, `pom.xml`, `test-plan.md` |
| Jenkins environment setup (25) | `jenkins/Dockerfile`, `jenkins/plugins.txt`, `docker-compose.yml`, Blue Ocean |
| Pipeline functionality (40) | `Jenkinsfile` (checkout/build/test/archive), live GitHub webhook |
| Notification system (15) | Slack `post { success/failure }` block in `Jenkinsfile` |
