FROM node:20-slim
# Non-root user (vscode) creation is delegated to the common-utils devcontainer
# feature in devcontainer.json to avoid GID conflicts with the base image.

RUN apt-get update && apt-get install -y --no-install-recommends \
    git curl \
    && rm -rf /var/lib/apt/lists/*

RUN npx playwright install-deps chromium
