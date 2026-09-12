#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if [[ -f .env ]]; then
    set -a
    source .env
    set +a
fi
case "${1:-run}" in
    docker) exec docker compose up --build ;;
    build) exec mvn -B package -DskipTests ;;
    test) exec mvn -B test ;;
    integration) exec mvn -B verify ;;
    run) exec mvn spring-boot:run ;;
    jar) exec java -jar target/flashsale-0.0.1-SNAPSHOT.jar ;;
    help|--help|-h) echo "Usage: ./start.sh [run|docker|build|jar|test|integration]" ;;
    *) echo "Unknown command: $1" >&2; exit 1 ;;
esac
