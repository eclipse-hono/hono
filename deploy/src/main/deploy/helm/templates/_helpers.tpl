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
Configuration for the health check server of service components.
If the scope passed in is not nil, then it is used as the
configuration for the health check server. Otherwise, a secure health check
server will be configured to bind to all interfaces on the default port
using the component's key and cert.
*/}}
{{- define "hono.healthServerConfig" -}}
healthCheck:
{{- if . }}
  {{- toYaml . | nindent 2 }}
{{- else }}
  port: ${vertx.health.port}
  bindAddress: 0.0.0.0
  keyPath: /etc/hono/key.pem
  certPath: /etc/hono/cert.pem
{{- end }}
{{- end }}


{{/*
Configuration for the service clients of protocol adapters.
The scope passed in is expected to be a dict with keys
- "dot": the root scope (".") and
- "component": the name of the adapter

The component name is used to construct the names of the key and cert
PEM files by appending "-key.pem" and "-cert.pem" respectively.
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
  {{- toYaml .dot.Values.amqpMessagingNetworkSpec }}
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
  {{- toYaml .dot.Values.commandAndControlSpec }}
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
  {{- toYaml .dot.Values.tenantSpec }}
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
  {{- toYaml .dot.Values.deviceRegistrationSpec }}
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
  {{- toYaml .dot.Values.credentialsSpec }}
{{- end }}
deviceConnection:
{{- if .dot.Values.deviceRegistryDeployExample }}
  name: Hono {{ $adapter }}
  host: {{ .dot.Release.Name }}-service-device-registry
  port: 5671
  credentialsPath: /etc/hono/adapter.credentials
  trustStorePath: /etc/hono/trusted-certs.pem
  hostnameVerificationRequired: false
{{- else }}
  {{- toYaml .dot.Values.deviceConnectionSpec }}
{{- end }}
{{- end }}

