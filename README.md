# pit-wall

*Pit Wall* is an event-driven telemetry platform for Formula 1 data. An ingest service replays historical session data from the OpenF1 API into Kafka, where a stream processor turns raw car and timing events into race metrics. The results are stored in Postgres, served through a REST API, and visualized in Grafana dashboards. The system runs on Kubernetes, with GitHub Actions handling builds, tests and deployments. I'm building it to get hands-on experience with containers, event streaming and orchestration, using real-world data I genuinely enjoy working with.

*Status*: Early development. Currently containerizing the ingest service with Docker.
