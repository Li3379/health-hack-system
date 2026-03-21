# Multi-stage Dockerfile for Vue frontend
# Build stage: Compile Vue application
FROM node:20-alpine AS build

WORKDIR /app

# Copy package files first for better caching
COPY package*.json ./

# Clean install dependencies (uses lockfile for reproducibility)
RUN npm ci

# Copy source files
COPY . .

# Build the application
RUN npm run build

# Runtime stage: Serve with nginx
FROM nginx:alpine

# Copy nginx configuration
COPY nginx.conf /etc/nginx/nginx.conf

# Copy build output from build stage
COPY --from=build /app/dist /usr/share/nginx/html

# Health check for container orchestration
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://127.0.0.1/ || exit 1

# Expose port 80
EXPOSE 80

# Start nginx in foreground
CMD ["nginx", "-g", "daemon off;"]