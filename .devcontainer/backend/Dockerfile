FROM eclipse-temurin:21.0.6_7-jdk
# Non-root user (vscode) creation is delegated to the common-utils devcontainer
# feature in devcontainer.json to avoid GID conflicts with the base image.

RUN apt-get update && apt-get install -y --no-install-recommends \
    git curl wget unzip \
    && rm -rf /var/lib/apt/lists/*
