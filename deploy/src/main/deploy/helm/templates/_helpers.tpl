{{/* vim: set filetype=mustache: */}}
{{/*
Expand the name of the chart.
*/}}
{{- define "hono.name" -}}
  {{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "hono.fullname" -}}
  {{- if .Values.fullnameOverride -}}
    {{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
  {{- else -}}
    {{- $name := default .Chart.Name .Values.nameOverride -}}
    {{- if contains $name .Release.Name -}}
      {{- .Release.Name | trunc 63 | trimSuffix "-" -}}
    {{- else -}}
      {{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" -}}
    {{- end -}}
  {{- end -}}
{{- end -}}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "hono.chart" }}
  {{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Add standard labels for resources as recommended by Helm best practices.
*/}}
{{- define "hono.std.labels" -}}
app.kubernetes.io/name: {{ template "hono.name" . }}
helm.sh/chart: {{ template "hono.chart" . }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion }}
{{- end }}

{{/*
Add standard labels and name for resources as recommended by Helm best practices.
The scope passed in is expected to be a dict with keys
- "dot": the "." scope and
- "name": the value to use for the "name" metadata property
- "component": the value to use for the "app.kubernetes.io/component" label
*/}}
{{- define "hono.metadata" -}}
name: {{ .dot.Release.Name }}-{{ .name }}
labels:
  app.kubernetes.io/name: {{ template "hono.name" .dot }}
  helm.sh/chart: {{ template "hono.chart" .dot }}
  app.kubernetes.io/managed-by: {{ .dot.Release.Service }}
  app.kubernetes.io/instance: {{ .dot.Release.Name }}
  app.kubernetes.io/version: {{ .dot.Chart.AppVersion }}
  {{- if .component }}
  app.kubernetes.io/component: {{ .component }}
  {{- end }}
{{- end }}

{{/*
Add standard match labels to be used in podTemplateSpecs and serviceMatchers.
The scope passed in is expected to be a dict with keys
- "dot": the "." scope and
- "component": the value of the "app.kubernetes.io/component" label to match
*/}}
{{- define "hono.matchLabels" -}}
app.kubernetes.io/name: {{ template "hono.name" .dot }}
app.kubernetes.io/instance: {{ .dot.Release.Name }}
app.kubernetes.io/component: {{ .component }}
{{- end }}


{{/*
Creates a headless Service for a Hono component.
The scope passed in is expected to be a dict with keys
- "dot": the "." scope and
- "name": the value to use for the "name" metadata property
- "component": the value of the "app.kubernetes.io/component" label to match
*/}}
{{- define "hono.headless.service" }}
{{- $args := dict "dot" .dot "component" .component "name" (printf "%s-headless" .name) }}
---
apiVersion: v1
kind: Service
metadata:
  {{- include "hono.metadata" $args | nindent 2 }}
spec:
  clusterIP: None
  selector:
    {{- include "hono.matchLabels" $args | nindent 4 }}
{{- end }}


{{/*
Configuration for the health check server of service components.
If the scope passed in is not 'nil', then its value is
used as the configuration for the health check server.
Otherwise, a secure health check server will be configured to bind to all
interfaces on the default port using the component's key and cert.
*/}}
{{- define "hono.healthServerConfig" -}}
healthCheck:
{{- if . }}
  {{- toYaml . | nindent 2 }}
{{- else }}
  port: ${vertx.health.port}
  bindAddress: "0.0.0.0"
  keyPath: "/etc/hono/key.pem"
  certPath: "/etc/hono/cert.pem"
{{- end }}
{{- end }}


{{/*
Configuration for the service clients of protocol adapters.
The scope passed in is expected to be a dict with keys
- "dot": the root scope (".") and
- "component": the name of the adapter
*/}}
{{- define "hono.serviceClientConfig" -}}
{{- $adapter := default "adapter" .component -}}
messaging:
{{- if .dot.Values.amqpMessagingNetworkDeployExample }}
  name: Hono {{ $adapter }}
  amqpHostname: hono-internal
  host: {{ .dot.Release.Name }}-dispatch-router
  port: 5673
  keyPath: /etc/hono/key.pem
  certPath: /etc/hono/cert.pem
  trustStorePath: /etc/hono/trusted-certs.pem
  hostnameVerificationRequired: false
{{- else }}
  {{- required ".Values.adapters.amqpMessagingNetworkSpec MUST be set if example AQMP Messaging Network is disabled" .dot.Values.adapters.amqpMessagingNetworkSpec | toYaml | nindent 2 }}
{{- end }}
command:
{{- if .dot.Values.amqpMessagingNetworkDeployExample }}
  name: Hono {{ $adapter }}
  amqpHostname: hono-internal
  host: {{ .dot.Release.Name }}-dispatch-router
  port: 5673
  keyPath: /etc/hono/key.pem
  certPath: /etc/hono/cert.pem
  trustStorePath: /etc/hono/trusted-certs.pem
  hostnameVerificationRequired: false
{{- else }}
  {{- required ".Values.adapters.commandAndControlSpec MUST be set if example AQMP Messaging Network is disabled" .dot.Values.adapters.commandAndControlSpec | toYaml | nindent 2 }}
{{- end }}
tenant:
{{- if .dot.Values.deviceRegistryDeployExample }}
  name: Hono {{ $adapter }}
  host: {{ .dot.Release.Name }}-service-device-registry
  port: 5671
  credentialsPath: /etc/hono/adapter.credentials
  trustStorePath: /etc/hono/trusted-certs.pem
  hostnameVerificationRequired: false
{{- else }}
  {{- required ".Values.adapters.tenantSpec MUST be set if example Device Registry is disabled" .dot.Values.adapters.tenantSpec | toYaml | nindent 2 }}
{{- end }}
registration:
{{- if .dot.Values.deviceRegistryDeployExample }}
  name: Hono {{ $adapter }}
  host: {{ .dot.Release.Name }}-service-device-registry
  port: 5671
  credentialsPath: /etc/hono/adapter.credentials
  trustStorePath: /etc/hono/trusted-certs.pem
  hostnameVerificationRequired: false
{{- else }}
  {{- required ".Values.adapters.deviceRegistrationSpec MUST be set if example Device Registry is disabled" .dot.Values.adapters.deviceRegistrationSpec | toYaml | nindent 2 }}
{{- end }}
credentials:
{{- if .dot.Values.deviceRegistryDeployExample }}
  name: Hono {{ $adapter }}
  host: {{ .dot.Release.Name }}-service-device-registry
  port: 5671
  credentialsPath: /etc/hono/adapter.credentials
  trustStorePath: /etc/hono/trusted-certs.pem
  hostnameVerificationRequired: false
{{- else }}
  {{- required ".Values.adapters.credentialsSpec MUST be set if example Device Registry is disabled" .dot.Values.adapters.credentialsSpec | toYaml | nindent 2 }}
{{- end }}
deviceConnection:
{{- if .dot.Values.adapters.deviceConnectionSpec }}
  {{- range $key, $value := .dot.Values.adapters.deviceConnectionSpec }}
  {{ $key }}: {{ $value }}
  {{- end }}
{{- else }}
  name: Hono {{ $adapter }}
  {{- if .dot.Values.deviceConnectionService.enabled }}
  host: {{ .dot.Release.Name }}-service-device-connection
  {{- else }}
    {{- if .dot.Values.deviceRegistryDeployExample }}
  host: {{ .dot.Release.Name }}-service-device-registry
    {{- else }}
      {{- required ".Values.deviceConnectionService.enabled MUST be set to true if example Device Registry is disabled and no other Device Connection service is configured" nil }}
    {{- end }}
  {{- end }}
  port: 5671
  credentialsPath: /etc/hono/adapter.credentials
  trustStorePath: /etc/hono/trusted-certs.pem
  hostnameVerificationRequired: false
{{- end }}
{{- end }}

{{/*
Create a fully qualified Prometheus server name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
*/}}
{{- define "hono.prometheus.server.fullname" -}}
{{- if .Values.prometheus.server.fullnameOverride -}}
{{- .Values.prometheus.server.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- $name := default "prometheus" .Values.prometheus.nameOverride -}}
{{- if contains $name .Release.Name -}}
{{- printf "%s-%s" .Release.Name .Values.prometheus.server.name | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s-%s" .Release.Name $name .Values.prometheus.server.name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}
{{- end -}}


{{/*
Create a scrape job for a service name.
The scope passed in is expected to be a dict with keys
- "dot": the root scope (".") and
- "serviceName": the name of the service to scrape

*/}}
{{- define "hono.prometheus.scrapeJob" }}
- job_name: {{ printf "%s-%s" .dot.Release.Name .serviceName }}
  metrics_path: /prometheus
  scheme: https
  tls_config:
    insecure_skip_verify: true
  dns_sd_configs:
  - names:
    - {{ printf "%s-%s-headless" .dot.Release.Name .serviceName }}
    type: A
    port: {{ default ${prometheus.scraping.port} .dot.Values.monitoring.prometheus.port }}
    refresh_interval: 10s
{{- end }}

{{/*
Adds a Jaeger Agent container to a template spec.
*/}}
{{- define "hono.jaeger.agent" }}
{{- $jaegerEnabled := or .Values.jaegerBackendDeployExample .Values.jaegerAgentConf }}
{{- if $jaegerEnabled }}
- name: jaeger-agent-sidecar
  image: {{ default "jaegertracing/jaeger-agent:1.13.1" .Values.jaegerAgentImage }}
  ports:
  - name: agent-compact
    containerPort: 6831
    protocol: UDP
  - name: agent-binary
    containerPort: 6832
    protocol: UDP
  - name: agent-configs
    containerPort: 5778
    protocol: TCP
  readinessProbe:
    httpGet:
      path: "/"
      port: 14271
    initialDelaySeconds: 5
  env:
  {{- if .Values.jaegerBackendDeployExample }}
  - name: REPORTER_TYPE
    value: "tchannel"
  - name: REPORTER_TCHANNEL_HOST_PORT
    value: {{ printf "%s-jaeger-collector:14267" .Release.Name | quote }}
  - name: REPORTER_TCHANNEL_DISCOVERY_MIN_PEERS
    value: "1"
  {{- else }}
  {{- range $key, $value := .Values.jaegerAgentConf }}
  - name: {{ $key }}
    value: {{ $value | quote }}
  {{- end }}
  {{- end }}
{{- end }}
{{- end }}

{{/*
Adds Jaeger client configuration to a container's "env" properties.
The scope passed in is expected to be a dict with keys
- "dot": the root scope (".") and
- "name": the value to use for the JAEGER_SERVICE_NAME (prefixed with the release name).
*/}}
{{- define "hono.jaeger.clientConf" }}
{{- $agentHost := printf "%s-jaeger-agent" .dot.Release.Name }}
- name: JAEGER_SERVICE_NAME
  value: {{ printf "%s-%s" .dot.Release.Name .name | quote }}
{{- if .dot.Values.jaegerBackendDeployExample }}
- name: JAEGER_SAMPLER_TYPE
  value: "const"
- name: JAEGER_SAMPLER_PARAM
  value: "1"
{{- end }}
{{- end }}
