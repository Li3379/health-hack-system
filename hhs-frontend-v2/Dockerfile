# Multi-stage Dockerfile for Vue frontend
# Build stage: Compile Vue application
FROM node:20-alpine AS build

# Build arguments for environment variables
ARG VITE_API_BASE_URL
ARG VITE_WS_BASE_URL

# Set environment variables for build
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
ENV VITE_WS_BASE_URL=${VITE_WS_BASE_URL}

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