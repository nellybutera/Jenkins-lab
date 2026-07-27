# Jenkins CI/CD Lab — Automation Test Execution

## What this is

A lab submission for AmaliTech's QA training program. The assignment: take an automated test suite and wire
it into Jenkins so that a `git push` — not a manual click, not a timer — pulls the latest code, installs
dependencies, runs the suite, publishes a report, and posts the result to Slack.

Three pieces, wired together:
- A REST Assured test suite against a public API
- A Jenkins instance running in Docker
- A real GitHub webhook that triggers a build the moment code is pushed

## The test suite

11 REST Assured + JUnit 5 tests against [FakeStoreAPI](https://fakestoreapi.com)'s `/products` endpoint —
GET, POST, PUT, DELETE, JSON schema validation, Allure reporting. Full test case documentation, including
strategy and rationale, is in [`test-plan.md`](./test-plan.md).

Worth calling out: before writing any assertion, I probed the live API with `curl` instead of assuming
standard REST behavior. It doesn't follow the textbook — a `GET` on a missing id returns `200` with an empty
body instead of `404`, and `DELETE` echoes back the full original object instead of an empty response. The
suite asserts what the API actually does, and those quirks are documented as observed behavior, not bugs.

## Architecture decisions

- **Jenkins runs in Docker**, built from a custom image with Maven 3.9.16 and every required plugin (git,
  github, pipeline, credentials-binding, htmlpublisher, junit, Blue Ocean) baked in via `plugins.txt` — so the
  whole environment is reproducible from this repo, no manual plugin installs.
- **`jenkins_home` is a named Docker volume, not a bind mount** — this repo lives inside a OneDrive-synced
  folder, and Jenkins writes thousands of small files constantly. Bind-mounting that into OneDrive means
  OneDrive tries to sync every write in real time and fights Jenkins for file locks. A named volume keeps
  Jenkins' state outside OneDrive's reach entirely.
- **Maven runs directly in the Jenkins image, no Docker-in-Docker** — the pipeline only checks out, builds,
  and tests; it never needs to build an image itself. Containerization is proven separately, by this repo's
  own `Dockerfile`.
- **Triggered by a real GitHub webhook, not a polling schedule** — the lab grades webhook integration
  specifically, and a polling job would produce a similar result through an easier, different mechanism that
  doesn't demonstrate the same thing.

## Getting a public URL to Jenkins: why ngrok, and not Vercel

Jenkins runs locally with no public address, so a real webhook needs something exposing it to the internet.
A few options came up while figuring this out:

- **Vercel doesn't fit at all** — it deploys serverless functions and static frontends *to* its own
  infrastructure. There's no mechanism for it to tunnel traffic back down to a process running on a personal
  machine, which is exactly what's needed here.
- **Cloudflare Tunnel / Tailscale Funnel** were real alternatives — both free, both capable of a stable
  hostname — but both need more setup (a domain in Cloudflare's DNS, or joining a Tailscale network) for what
  is, in the end, a lab environment.
- **ngrok** won on setup cost: a free static domain (so the URL doesn't change every restart, unlike ngrok's
  default random subdomain) and a single command to stand up a tunnel.
- The native Windows `ngrok.exe`, however, got **flagged and blocked by Windows Defender** ("contains a virus
  or potentially unwanted software" — a known heuristic false positive against tunneling tools generally).
  Rather than disabling antivirus protection to force it through, **ngrok runs as a Docker container instead**,
  on the same Docker network as Jenkins, addressing the Jenkins container directly by name. Sidesteps the
  Windows-specific problem and removes the native install dependency entirely.

## A couple of things that broke

- **The webhook silently did nothing at first.** Checking GitHub's actual delivery log (not just assuming the
  green "Add webhook" checkmark meant success) showed the real cause: the payload URL was missing a trailing
  slash. Jenkins redirects `/github-webhook` instead of processing it, and GitHub logs that redirect as a
  failed delivery rather than following it.
- **Jenkins blocked its own Allure report from rendering** — its default Content-Security-Policy blocks the
  inline JavaScript Allure's report needs, so the report link showed a blank page even though the report
  itself generated correctly. One Script Console command fixed it
  (`System.setProperty("hudson.model.DirectoryBrowserSupport.CSP", "")`); the JUnit trend graph on the same
  build page always showed real data regardless.

## Verified, not assumed

- Test suite confirmed green in three separate environments — locally, inside its own Docker image, and
  freshly cloned inside the running Jenkins container — before trusting the pipeline to run it unattended.
- Webhook trigger confirmed by pushing a real commit and reading both GitHub's delivery log and Jenkins' build
  history, matching a specific commit hash through checkout → build → test → pass with no manual step.

## Repository layout

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

## Running it

```bash
docker compose up -d          # start Jenkins (jenkins_home persists across restarts)
docker start ngrok-tunnel     # resume the public webhook tunnel
mvn test                                                                  # run the suite locally
mvn allure:serve                                                          # view the Allure report
docker build -t jenkins-lab-tests . && docker run --rm jenkins-lab-tests  # containerized run
```

Both Jenkins and the ngrok tunnel need to be running at the same time as a push for the webhook to fire —
neither is a "set once" configuration.
