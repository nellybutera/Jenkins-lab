# Jenkins CI/CD Lab — Automation Test Execution

## What this is

This is a lab submission for AmaliTech's QA training program. The assignment: take an automated test suite,
put it in front of Jenkins, and make Jenkins do what a human tester shouldn't have to do by hand — pull the
latest code the moment it's pushed, install dependencies, run the full suite, publish a report, and tell
someone the result. In other words: turn "I have tests" into "tests run themselves, on every change, and
someone gets told what happened."

Concretely, that meant building three things and wiring them together: a REST Assured test suite against a
public API, a Jenkins instance running in Docker, and a real GitHub webhook so a `git push` — not a scheduled
timer, not a manual click — is what starts a build.

## The test suite

11 REST Assured + JUnit 5 tests against [FakeStoreAPI](https://fakestoreapi.com)'s `/products` endpoint —
GET, POST, PUT, DELETE, JSON schema validation, Allure reporting. Full test case documentation is in
[`test-plan.md`](./test-plan.md).

The one thing worth calling out here: before writing a single assertion, I hit the live API with `curl` to see
what it actually does, rather than assuming it behaves like a textbook REST API. It doesn't. A `GET` on a
product id that doesn't exist returns `200` with an empty body instead of `404`. A `DELETE` echoes back the
*entire original object* instead of an empty response. Copying assertions from a previous lab (which targeted
a different, better-behaved mock API) would have produced tests that fail against the real, correct behavior
of this one. So the suite asserts what FakeStoreAPI actually does, and `test-plan.md` documents those quirks
explicitly as observed behavior — not bugs in the suite, not bugs in the API, just a mock service that doesn't
follow the rules a "real" REST API would.

## Architecture decisions

**Jenkins runs in Docker, built from a custom image** (`jenkins/Dockerfile`) on top of `jenkins/jenkins:lts-jdk17`,
with Maven 3.9.16 and every required plugin (git, github, pipeline, credentials-binding, htmlpublisher, junit,
Blue Ocean) baked in via `plugins.txt`. The point of baking all of this into the image rather than installing
plugins by hand through the UI is reproducibility — anyone can rebuild the exact same Jenkins from this repo
without a single manual setup click beyond the one-time admin account.

**`jenkins_home` is a named Docker volume, not a bind mount, and that decision was forced by where this repo
lives.** It's inside a OneDrive-synced folder. Jenkins' home directory is thousands of small files that get
written constantly during a build; if that directory were bind-mounted into a OneDrive folder, OneDrive would
try to sync every one of those writes in real time and fight Jenkins for file locks — which reliably corrupts
a Jenkins instance. A named volume keeps Jenkins' internal state entirely outside OneDrive's reach while
staying attached to the container.

**Maven runs directly inside the Jenkins container — no Docker-in-Docker.** The pipeline's job is checkout,
install dependencies, run tests, publish a report. None of that requires the pipeline itself to build a Docker
image, so there was no reason to mount a Docker socket into Jenkins and take on that complexity. The
containerization requirement is satisfied separately and directly: this repo's own `Dockerfile` builds and
runs the test suite in isolation, verified independently of Jenkins entirely.

**The pipeline is triggered by a real GitHub webhook, not a polling schedule.** Jenkins *can* just ask GitHub
"did anything change?" on a timer — that would have been far less setup. I didn't do that, because the lab
explicitly calls out webhook integration as its own requirement, and a polling job doesn't demonstrate that
skill; it just produces a similar-looking result through a different, easier mechanism. Since Jenkins runs
locally with no public address, getting a real webhook working meant exposing it through a tunnel (ngrok),
which turned out to be the most failure-prone part of the whole build (see below).

## Blockers, and how they got resolved

**The Allure report generator broke on a version that no longer exists.** The `allure-maven` plugin downloads
the Allure commandline tool as a zip from GitHub releases at build time. The version originally pinned had
been pruned from GitHub's release list entirely — a 404, not a flaky network error. Fixed by checking GitHub's
actual current releases and decoupling the commandline tool's version from the Java library version in
`pom.xml`, so a future prune of one doesn't silently break the other.

**Jenkins blocked its own Allure report from rendering.** Jenkins' default Content-Security-Policy blocks
inline JavaScript on HTML it serves through the HTML Publisher plugin, and Allure's report is a JS-driven
single-page app — so the "Allure Report" link on a build page rendered a blank page even though the report
itself was generated correctly. The JUnit trend graph on the same build page always showed real data
regardless; the Allure page itself needed one Script Console command
(`System.setProperty("hudson.model.DirectoryBrowserSupport.CSP", "")`) to actually display.

**The webhook silently did nothing, for a one-character reason.** After registering it on GitHub, pushes
weren't triggering builds — and the failure was quiet, not an error message anywhere obvious. Checking
GitHub's own webhook delivery log (not just assuming a green checkmark meant success) showed the actual
problem: the payload URL was missing a trailing slash (`/github-webhook` instead of `/github-webhook/`).
Jenkins redirects the un-slashed path instead of processing it, and GitHub logs that redirect as a failed
delivery rather than following it. One character, but nothing about the setup *looked* broken until the
delivery log was actually read.

**The native ngrok binary got blocked by Windows Defender**, flagged as "contains a virus or potentially
unwanted software" — a known heuristic false positive against tunneling tools in general, not anything
specific to this setup. Rather than disabling antivirus protection to force it through, ngrok runs as a Docker
container instead, on the same Docker network as Jenkins, addressing the Jenkins container directly by its
container name. That sidestepped the Windows-specific problem entirely and, incidentally, made the whole setup
more portable — it no longer depends on a native Windows install at all.

## Verifying it actually works, not just assuming it would

Every layer was proven independently rather than trusted to "probably still work" once assembled: the test
suite passing on the local machine doesn't guarantee it passes inside a container with a different filesystem
and no cached dependencies, and it doesn't guarantee it passes inside the *Jenkins* container specifically,
which is yet another environment. So the suite was run and confirmed green in all three places — locally,
inside its own Docker image, and freshly cloned inside the running Jenkins container — before ever trusting
the pipeline to run it unattended. The webhook trigger was verified the same way: not by assuming the GitHub
UI's "Add webhook" button worked, but by pushing a real commit and reading GitHub's delivery log and Jenkins'
own build history to confirm a specific commit hash was checked out, built, and passed, with no manual
intervention.

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

Both Jenkins and the ngrok tunnel need to be running at the same time as a push for the webhook to actually
fire — neither is a "set once" configuration, both are live processes that have to be up at the moment code
changes.
