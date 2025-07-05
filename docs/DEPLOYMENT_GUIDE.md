# VulnPatcher Deployment Guide

## Table of Contents

1. [Overview](#overview)
2. [Deployment Options](#deployment-options)
3. [Local Development](#local-development)
4. [Docker Deployment](#docker-deployment)
5. [Kubernetes Deployment](#kubernetes-deployment)
6. [Cloud Deployments](#cloud-deployments)
7. [Production Configuration](#production-configuration)
8. [Security Hardening](#security-hardening)
9. [Monitoring & Observability](#monitoring--observability)
10. [Backup & Recovery](#backup--recovery)
11. [Scaling & Performance](#scaling--performance)
12. [Troubleshooting](#troubleshooting)

## Overview

This guide covers deploying VulnPatcher in various environments, from local development to production-grade cloud deployments. VulnPatcher is built with Quarkus and uses LangChain4j for AI capabilities.

### Architecture Components

- **VulnPatcher Core**: Main application (Quarkus)
- **Ollama**: AI model runtime
- **PostgreSQL**: Metadata storage (optional)
- **Redis**: Caching layer (optional)
- **Prometheus**: Metrics collection
- **Grafana**: Metrics visualization

## Deployment Options

### Quick Comparison

| Option | Best For | Complexity | Scalability |
|--------|----------|------------|-------------|
| Local | Development | Low | None |
| Docker | Single server | Medium | Vertical |
| Kubernetes | Production | High | Horizontal |
| Cloud PaaS | Managed deployment | Medium | Auto-scaling |

## Local Development

### Prerequisites

```bash
# Check Java version (17+ required)
java -version

# Check Maven version (3.8+ required)
mvn -version

# Check Ollama installation
ollama --version
```

### Setup Steps

1. **Clone Repository**
   ```bash
   git clone https://github.com/intelliswarm/vuln-patcher.git
   cd vuln-patcher
   ```

2. **Install Ollama Models**
   ```bash
   # Start Ollama service
   ollama serve &
   
   # Pull required models
   ollama pull deepseek-coder:33b
   ollama pull mixtral:8x7b
   ollama pull codellama:34b
   ```

3. **Configure Environment**
   ```bash
   # Create .env file
   cat > .env << EOF
   GITHUB_TOKEN=your_github_token
   GITLAB_TOKEN=your_gitlab_token
   OLLAMA_BASE_URL=http://localhost:11434
   EOF
   
   # Source environment
   source .env
   ```

4. **Build Application**
   ```bash
   mvn clean package
   ```

5. **Run in Dev Mode**
   ```bash
   mvn quarkus:dev
   ```

### Development Features

- Hot reload enabled
- Dev UI at http://localhost:8080/q/dev
- Continuous testing with `mvn quarkus:test`

## Docker Deployment

### Single Container

1. **Create Dockerfile**
   ```dockerfile
   # Multi-stage build
   FROM maven:3.9-eclipse-temurin-17 AS build
   WORKDIR /app
   COPY pom.xml .
   RUN mvn dependency:go-offline
   COPY src ./src
   RUN mvn clean package -DskipTests

   # Runtime stage
   FROM eclipse-temurin:17-jre-alpine
   WORKDIR /app
   COPY --from=build /app/target/quarkus-app/lib/ /app/lib/
   COPY --from=build /app/target/quarkus-app/*.jar /app/
   COPY --from=build /app/target/quarkus-app/app/ /app/app/
   COPY --from=build /app/target/quarkus-app/quarkus/ /app/quarkus/

   EXPOSE 8080
   CMD ["java", "-jar", "quarkus-run.jar"]
   ```

2. **Build Image**
   ```bash
   docker build -t vulnpatcher:latest .
   ```

3. **Run Container**
   ```bash
   docker run -d \
     --name vulnpatcher \
     -p 8080:8080 \
     -e GITHUB_TOKEN=$GITHUB_TOKEN \
     -e OLLAMA_BASE_URL=http://host.docker.internal:11434 \
     vulnpatcher:latest
   ```

### Docker Compose

1. **Create docker-compose.yml**
   ```yaml
   version: '3.8'

   services:
     vulnpatcher:
       build: .
       container_name: vulnpatcher
       ports:
         - "8080:8080"
       environment:
         - GITHUB_TOKEN=${GITHUB_TOKEN}
         - GITLAB_TOKEN=${GITLAB_TOKEN}
         - OLLAMA_BASE_URL=http://ollama:11434
         - DB_HOST=postgres
         - REDIS_HOST=redis
       depends_on:
         - ollama
         - postgres
         - redis
       networks:
         - vulnpatcher-net
       volumes:
         - ./config:/app/config
       restart: unless-stopped

     ollama:
       image: ollama/ollama:latest
       container_name: ollama
       ports:
         - "11434:11434"
       volumes:
         - ollama_data:/root/.ollama
       networks:
         - vulnpatcher-net
       deploy:
         resources:
           reservations:
             devices:
               - driver: nvidia
                 count: 1
                 capabilities: [gpu]

     postgres:
       image: postgres:15-alpine
       container_name: postgres
       environment:
         - POSTGRES_DB=vulnpatcher
         - POSTGRES_USER=vulnpatcher
         - POSTGRES_PASSWORD=${DB_PASSWORD}
       volumes:
         - postgres_data:/var/lib/postgresql/data
       networks:
         - vulnpatcher-net

     redis:
       image: redis:7-alpine
       container_name: redis
       command: redis-server --appendonly yes
       volumes:
         - redis_data:/data
       networks:
         - vulnpatcher-net

     prometheus:
       image: prom/prometheus:latest
       container_name: prometheus
       volumes:
         - ./monitoring/prometheus.yml:/etc/prometheus/prometheus.yml
         - prometheus_data:/prometheus
       command:
         - '--config.file=/etc/prometheus/prometheus.yml'
         - '--storage.tsdb.path=/prometheus'
       ports:
         - "9090:9090"
       networks:
         - vulnpatcher-net

     grafana:
       image: grafana/grafana:latest
       container_name: grafana
       ports:
         - "3000:3000"
       environment:
         - GF_SECURITY_ADMIN_PASSWORD=${GRAFANA_PASSWORD}
       volumes:
         - grafana_data:/var/lib/grafana
         - ./monitoring/grafana/dashboards:/etc/grafana/provisioning/dashboards
       networks:
         - vulnpatcher-net

   networks:
     vulnpatcher-net:
       driver: bridge

   volumes:
     ollama_data:
     postgres_data:
     redis_data:
     prometheus_data:
     grafana_data:
   ```

2. **Create Monitoring Config**
   ```yaml
   # monitoring/prometheus.yml
   global:
     scrape_interval: 15s
     evaluation_interval: 15s

   scrape_configs:
     - job_name: 'vulnpatcher'
       static_configs:
         - targets: ['vulnpatcher:8080']
       metrics_path: '/metrics'
   ```

3. **Deploy Stack**
   ```bash
   # Create .env file with secrets
   echo "DB_PASSWORD=secure_password" >> .env
   echo "GRAFANA_PASSWORD=admin_password" >> .env
   
   # Start all services
   docker-compose up -d
   
   # Check status
   docker-compose ps
   
   # View logs
   docker-compose logs -f vulnpatcher
   ```

## Kubernetes Deployment

### Prerequisites

- Kubernetes cluster (1.25+)
- kubectl configured
- Helm 3 (optional)

### Namespace Setup

```bash
kubectl create namespace vulnpatcher
kubectl config set-context --current --namespace=vulnpatcher
```

### ConfigMaps and Secrets

1. **Create Secrets**
   ```bash
   kubectl create secret generic vulnpatcher-secrets \
     --from-literal=github-token=$GITHUB_TOKEN \
     --from-literal=gitlab-token=$GITLAB_TOKEN \
     --from-literal=db-password=secure_password
   ```

2. **Create ConfigMap**
   ```bash
   kubectl create configmap vulnpatcher-config \
     --from-file=application.properties=src/main/resources/application.properties
   ```

### Deployment Manifests

1. **vulnpatcher-deployment.yaml**
   ```yaml
   apiVersion: apps/v1
   kind: Deployment
   metadata:
     name: vulnpatcher
     labels:
       app: vulnpatcher
   spec:
     replicas: 3
     selector:
       matchLabels:
         app: vulnpatcher
     template:
       metadata:
         labels:
           app: vulnpatcher
         annotations:
           prometheus.io/scrape: "true"
           prometheus.io/path: "/metrics"
           prometheus.io/port: "8080"
       spec:
         containers:
         - name: vulnpatcher
           image: vulnpatcher:latest
           ports:
           - containerPort: 8080
             name: http
           env:
           - name: KUBERNETES_NAMESPACE
             valueFrom:
               fieldRef:
                 fieldPath: metadata.namespace
           - name: GITHUB_TOKEN
             valueFrom:
               secretKeyRef:
                 name: vulnpatcher-secrets
                 key: github-token
           - name: OLLAMA_BASE_URL
             value: "http://ollama-service:11434"
           - name: DB_HOST
             value: "postgres-service"
           - name: REDIS_HOST
             value: "redis-service"
           resources:
             requests:
               memory: "2Gi"
               cpu: "1"
             limits:
               memory: "4Gi"
               cpu: "2"
           livenessProbe:
             httpGet:
               path: /health/live
               port: 8080
             initialDelaySeconds: 30
             periodSeconds: 10
           readinessProbe:
             httpGet:
               path: /health/ready
               port: 8080
             initialDelaySeconds: 5
             periodSeconds: 5
           volumeMounts:
           - name: config
             mountPath: /app/config
         volumes:
         - name: config
           configMap:
             name: vulnpatcher-config
   ```

2. **Service Definition**
   ```yaml
   apiVersion: v1
   kind: Service
   metadata:
     name: vulnpatcher-service
     labels:
       app: vulnpatcher
   spec:
     type: ClusterIP
     ports:
     - port: 80
       targetPort: 8080
       protocol: TCP
       name: http
     selector:
       app: vulnpatcher
   ```

3. **Ingress Configuration**
   ```yaml
   apiVersion: networking.k8s.io/v1
   kind: Ingress
   metadata:
     name: vulnpatcher-ingress
     annotations:
       kubernetes.io/ingress.class: nginx
       cert-manager.io/cluster-issuer: letsencrypt-prod
       nginx.ingress.kubernetes.io/rate-limit: "100"
   spec:
     tls:
     - hosts:
       - vulnpatcher.example.com
       secretName: vulnpatcher-tls
     rules:
     - host: vulnpatcher.example.com
       http:
         paths:
         - path: /
           pathType: Prefix
           backend:
             service:
               name: vulnpatcher-service
               port:
                 number: 80
   ```

4. **HorizontalPodAutoscaler**
   ```yaml
   apiVersion: autoscaling/v2
   kind: HorizontalPodAutoscaler
   metadata:
     name: vulnpatcher-hpa
   spec:
     scaleTargetRef:
       apiVersion: apps/v1
       kind: Deployment
       name: vulnpatcher
     minReplicas: 2
     maxReplicas: 10
     metrics:
     - type: Resource
       resource:
         name: cpu
         target:
           type: Utilization
           averageUtilization: 70
     - type: Resource
       resource:
         name: memory
         target:
           type: Utilization
           averageUtilization: 80
   ```

### Ollama Deployment

```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: ollama
spec:
  serviceName: ollama-service
  replicas: 1
  selector:
    matchLabels:
      app: ollama
  template:
    metadata:
      labels:
        app: ollama
    spec:
      containers:
      - name: ollama
        image: ollama/ollama:latest
        ports:
        - containerPort: 11434
        volumeMounts:
        - name: ollama-storage
          mountPath: /root/.ollama
        resources:
          requests:
            memory: "16Gi"
            cpu: "4"
            nvidia.com/gpu: 1  # If GPU available
          limits:
            memory: "32Gi"
            cpu: "8"
            nvidia.com/gpu: 1
  volumeClaimTemplates:
  - metadata:
      name: ollama-storage
    spec:
      accessModes: ["ReadWriteOnce"]
      resources:
        requests:
          storage: 100Gi
```

### Deploy Everything

```bash
# Deploy all resources
kubectl apply -f k8s/

# Check deployment status
kubectl rollout status deployment/vulnpatcher

# View pods
kubectl get pods

# Check logs
kubectl logs -f deployment/vulnpatcher
```

## Cloud Deployments

### AWS ECS/Fargate

1. **Task Definition**
   ```json
   {
     "family": "vulnpatcher",
     "networkMode": "awsvpc",
     "requiresCompatibilities": ["FARGATE"],
     "cpu": "4096",
     "memory": "8192",
     "containerDefinitions": [
       {
         "name": "vulnpatcher",
         "image": "your-ecr-repo/vulnpatcher:latest",
         "portMappings": [
           {
             "containerPort": 8080,
             "protocol": "tcp"
           }
         ],
         "environment": [
           {
             "name": "OLLAMA_BASE_URL",
             "value": "http://ollama.internal:11434"
           }
         ],
         "secrets": [
           {
             "name": "GITHUB_TOKEN",
             "valueFrom": "arn:aws:secretsmanager:region:account:secret:github-token"
           }
         ],
         "logConfiguration": {
           "logDriver": "awslogs",
           "options": {
             "awslogs-group": "/ecs/vulnpatcher",
             "awslogs-region": "us-east-1",
             "awslogs-stream-prefix": "ecs"
           }
         }
       }
     ]
   }
   ```

2. **Service Configuration**
   ```yaml
   # ecs-service.yaml
   apiVersion: v1
   kind: Service
   metadata:
     name: vulnpatcher
   spec:
     launchType: FARGATE
     taskDefinition: vulnpatcher:latest
     desiredCount: 3
     loadBalancers:
       - targetGroupArn: arn:aws:elasticloadbalancing:...
         containerName: vulnpatcher
         containerPort: 8080
   ```

### Google Cloud Run

```bash
# Build and push to GCR
gcloud builds submit --tag gcr.io/PROJECT_ID/vulnpatcher

# Deploy to Cloud Run
gcloud run deploy vulnpatcher \
  --image gcr.io/PROJECT_ID/vulnpatcher \
  --platform managed \
  --region us-central1 \
  --memory 4Gi \
  --cpu 2 \
  --timeout 3600 \
  --concurrency 100 \
  --set-env-vars="OLLAMA_BASE_URL=http://ollama-service:11434" \
  --set-secrets="GITHUB_TOKEN=github-token:latest"
```

### Azure Container Instances

```bash
# Create container group
az container create \
  --resource-group vulnpatcher-rg \
  --name vulnpatcher \
  --image vulnpatcher:latest \
  --cpu 2 \
  --memory 4 \
  --ports 8080 \
  --environment-variables \
    OLLAMA_BASE_URL=http://ollama:11434 \
  --secure-environment-variables \
    GITHUB_TOKEN=$GITHUB_TOKEN
```

## Production Configuration

### Environment-Specific Settings

1. **Production application.properties**
   ```properties
   # Quarkus Production Settings
   quarkus.http.port=8080
   quarkus.http.ssl-port=8443
   quarkus.http.ssl.certificate.files=/certs/tls.crt
   quarkus.http.ssl.certificate.key-files=/certs/tls.key
   
   # Database Configuration
   quarkus.datasource.db-kind=postgresql
   quarkus.datasource.username=${DB_USER}
   quarkus.datasource.password=${DB_PASSWORD}
   quarkus.datasource.jdbc.url=jdbc:postgresql://${DB_HOST}:5432/vulnpatcher
   quarkus.datasource.jdbc.max-size=20
   
   # Redis Cache
   quarkus.redis.hosts=redis://${REDIS_HOST}:6379
   quarkus.redis.password=${REDIS_PASSWORD}
   quarkus.redis.timeout=30s
   
   # Performance Tuning
   quarkus.thread-pool.core-threads=20
   quarkus.thread-pool.max-threads=100
   quarkus.vertx.worker-pool-size=40
   
   # Security
   quarkus.http.cors=true
   quarkus.http.cors.origins=https://app.example.com
   quarkus.http.auth.basic=false
   
   # Logging
   quarkus.log.level=INFO
   quarkus.log.category."ai.intelliswarm".level=INFO
   quarkus.log.console.json=true
   
   # Metrics
   quarkus.micrometer.export.prometheus.enabled=true
   quarkus.micrometer.export.prometheus.path=/metrics
   ```

2. **JVM Options**
   ```bash
   JAVA_OPTS="-Xms4g -Xmx8g \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=200 \
     -XX:+UseStringDeduplication \
     -Djava.net.preferIPv4Stack=true \
     -Dfile.encoding=UTF-8"
   ```

### Database Schema

```sql
-- Create database
CREATE DATABASE vulnpatcher;

-- Create user
CREATE USER vulnpatcher WITH ENCRYPTED PASSWORD 'secure_password';
GRANT ALL PRIVILEGES ON DATABASE vulnpatcher TO vulnpatcher;

-- Schema migrations handled by Flyway
-- See src/main/resources/db/migration/
```

## Security Hardening

### 1. Network Security

```yaml
# NetworkPolicy for Kubernetes
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: vulnpatcher-netpol
spec:
  podSelector:
    matchLabels:
      app: vulnpatcher
  policyTypes:
  - Ingress
  - Egress
  ingress:
  - from:
    - namespaceSelector:
        matchLabels:
          name: ingress-nginx
    ports:
    - protocol: TCP
      port: 8080
  egress:
  - to:
    - namespaceSelector: {}
    ports:
    - protocol: TCP
      port: 11434  # Ollama
    - protocol: TCP
      port: 5432   # PostgreSQL
    - protocol: TCP
      port: 6379   # Redis
  - to:
    - namespaceSelector: {}
    ports:
    - protocol: TCP
      port: 443    # External APIs
```

### 2. Secrets Management

```bash
# Use external secret manager
kubectl create secret generic vulnpatcher-secrets \
  --from-literal=github-token="" \
  --dry-run=client -o yaml | \
  kubeseal --format yaml > sealed-secrets.yaml
```

### 3. RBAC Configuration

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: vulnpatcher-role
rules:
- apiGroups: [""]
  resources: ["configmaps", "secrets"]
  verbs: ["get", "list", "watch"]
- apiGroups: [""]
  resources: ["pods"]
  verbs: ["get", "list"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: vulnpatcher-rolebinding
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: Role
  name: vulnpatcher-role
subjects:
- kind: ServiceAccount
  name: vulnpatcher-sa
```

### 4. Pod Security Policy

```yaml
apiVersion: policy/v1beta1
kind: PodSecurityPolicy
metadata:
  name: vulnpatcher-psp
spec:
  privileged: false
  allowPrivilegeEscalation: false
  requiredDropCapabilities:
    - ALL
  volumes:
    - 'configMap'
    - 'emptyDir'
    - 'projected'
    - 'secret'
    - 'persistentVolumeClaim'
  runAsUser:
    rule: 'MustRunAsNonRoot'
  seLinux:
    rule: 'RunAsAny'
  fsGroup:
    rule: 'RunAsAny'
```

## Monitoring & Observability

### 1. Prometheus Configuration

```yaml
# prometheus-config.yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'vulnpatcher'
    kubernetes_sd_configs:
    - role: pod
      namespaces:
        names:
        - vulnpatcher
    relabel_configs:
    - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_scrape]
      action: keep
      regex: true
    - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_path]
      action: replace
      target_label: __metrics_path__
      regex: (.+)
```

### 2. Grafana Dashboard

```json
{
  "dashboard": {
    "title": "VulnPatcher Monitoring",
    "panels": [
      {
        "title": "Scan Rate",
        "targets": [
          {
            "expr": "rate(vulnpatcher_scans_total[5m])"
          }
        ]
      },
      {
        "title": "Vulnerability Detection Rate",
        "targets": [
          {
            "expr": "rate(vulnpatcher_vulnerabilities_detected_total[5m])"
          }
        ]
      },
      {
        "title": "API Response Time",
        "targets": [
          {
            "expr": "histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))"
          }
        ]
      }
    ]
  }
}
```

### 3. Logging Configuration

```yaml
# fluent-bit configuration
apiVersion: v1
kind: ConfigMap
metadata:
  name: fluent-bit-config
data:
  fluent-bit.conf: |
    [SERVICE]
        Flush         5
        Log_Level     info
        Daemon        off

    [INPUT]
        Name              tail
        Tag               vulnpatcher.*
        Path              /var/log/containers/vulnpatcher*.log
        Parser            docker
        DB                /var/log/flb-vulnpatcher.db
        Mem_Buf_Limit     50MB

    [FILTER]
        Name              kubernetes
        Match             vulnpatcher.*
        Kube_URL          https://kubernetes.default.svc:443
        Kube_CA_File      /var/run/secrets/kubernetes.io/serviceaccount/ca.crt
        Kube_Token_File   /var/run/secrets/kubernetes.io/serviceaccount/token

    [OUTPUT]
        Name              es
        Match             vulnpatcher.*
        Host              elasticsearch.logging.svc.cluster.local
        Port              9200
        Index             vulnpatcher
        Type              _doc
```

### 4. Distributed Tracing

```properties
# OpenTelemetry configuration
quarkus.opentelemetry.enabled=true
quarkus.opentelemetry.tracer.exporter.otlp.endpoint=http://jaeger-collector:4317
quarkus.opentelemetry.tracer.exporter.otlp.headers=Authorization=Bearer ${OTLP_TOKEN}
```

## Backup & Recovery

### 1. Database Backup

```bash
#!/bin/bash
# backup-db.sh
DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="/backups"

# Backup PostgreSQL
pg_dump -h ${DB_HOST} -U ${DB_USER} -d vulnpatcher | \
  gzip > ${BACKUP_DIR}/vulnpatcher_${DATE}.sql.gz

# Upload to S3
aws s3 cp ${BACKUP_DIR}/vulnpatcher_${DATE}.sql.gz \
  s3://vulnpatcher-backups/db/

# Cleanup old backups (keep 30 days)
find ${BACKUP_DIR} -name "*.sql.gz" -mtime +30 -delete
```

### 2. Disaster Recovery Plan

```yaml
# CronJob for automated backups
apiVersion: batch/v1
kind: CronJob
metadata:
  name: backup-vulnpatcher
spec:
  schedule: "0 2 * * *"  # Daily at 2 AM
  jobTemplate:
    spec:
      template:
        spec:
          containers:
          - name: backup
            image: postgres:15-alpine
            command:
            - /bin/sh
            - -c
            - |
              pg_dump -h postgres-service -U vulnpatcher vulnpatcher | \
                gzip | aws s3 cp - s3://backups/vulnpatcher-$(date +%Y%m%d).sql.gz
            env:
            - name: PGPASSWORD
              valueFrom:
                secretKeyRef:
                  name: postgres-secret
                  key: password
          restartPolicy: OnFailure
```

## Scaling & Performance

### 1. Horizontal Scaling

```yaml
# KEDA ScaledObject for advanced autoscaling
apiVersion: keda.sh/v1alpha1
kind: ScaledObject
metadata:
  name: vulnpatcher-scaler
spec:
  scaleTargetRef:
    name: vulnpatcher
  minReplicaCount: 2
  maxReplicaCount: 20
  triggers:
  - type: prometheus
    metadata:
      serverAddress: http://prometheus:9090
      metricName: vulnpatcher_queue_size
      threshold: '10'
      query: vulnpatcher_scan_queue_size
  - type: cpu
    metadata:
      type: Utilization
      value: "70"
```

### 2. Caching Strategy

```properties
# Redis caching configuration
quarkus.cache.type=redis
quarkus.cache.redis.key-type=string
quarkus.cache.redis.value-type=json
quarkus.cache.redis.ttl=3600s

# Cache annotations in code
@CacheResult(cacheName = "vulnerability-cache")
@CacheInvalidate(cacheName = "vulnerability-cache")
```

### 3. Performance Tuning

```properties
# Connection pooling
quarkus.datasource.jdbc.min-size=10
quarkus.datasource.jdbc.max-size=50
quarkus.datasource.jdbc.acquisition-timeout=30

# HTTP settings
quarkus.http.io-threads=100
quarkus.http.worker-threads=200
quarkus.http.limits.max-body-size=50M
quarkus.http.idle-timeout=30M

# Vertx options
quarkus.vertx.max-event-loop-execute-time=30s
quarkus.vertx.warning-exception-time=20s
```

## Troubleshooting

### Common Issues

1. **High Memory Usage**
   ```bash
   # Check memory usage
   kubectl top pods
   
   # Get heap dump
   kubectl exec vulnpatcher-pod -- jcmd 1 GC.heap_dump /tmp/heap.hprof
   kubectl cp vulnpatcher-pod:/tmp/heap.hprof ./heap.hprof
   
   # Analyze with Eclipse MAT or similar
   ```

2. **Slow Response Times**
   ```bash
   # Enable debug logging
   kubectl set env deployment/vulnpatcher QUARKUS_LOG_LEVEL=DEBUG
   
   # Check slow queries
   kubectl logs -f deployment/vulnpatcher | grep "SLOW QUERY"
   ```

3. **Connection Issues**
   ```bash
   # Test connectivity
   kubectl run -it --rm debug --image=alpine --restart=Never -- sh
   # Inside pod:
   nc -zv ollama-service 11434
   nc -zv postgres-service 5432
   ```

### Health Check Endpoints

```bash
# Liveness
curl http://localhost:8080/health/live

# Readiness  
curl http://localhost:8080/health/ready

# Full health
curl http://localhost:8080/health
```

### Debug Mode

```bash
# Enable remote debugging
JAVA_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"

# Port forward for debugging
kubectl port-forward deployment/vulnpatcher 5005:5005
```

---

Last Updated: January 2024
Version: 1.0.0