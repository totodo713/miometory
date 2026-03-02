FROM eclipse-temurin:21.0.6_7-jdk
# Non-root user (vscode) creation is delegated to the common-utils devcontainer
# feature in devcontainer.json to avoid GID conflicts with the base image.

RUN apt-get update && apt-get install -y --no-install-recommends \
    git curl wget unzip ca-certificates gnupg \
    && mkdir -p /etc/apt/keyrings \
    && curl -fsSL https://deb.nodesource.com/gpgkey/nodesource-repo.gpg.key \
       | gpg --dearmor -o /etc/apt/keyrings/nodesource.gpg \
    && echo "deb [signed-by=/etc/apt/keyrings/nodesource.gpg] https://deb.nodesource.com/node_20.x nodistro main" \
       > /etc/apt/sources.list.d/nodesource.list \
    && apt-get update && apt-get install -y --no-install-recommends nodejs \
    && rm -rf /var/lib/apt/lists/*

RUN npx playwright install-deps chromium
