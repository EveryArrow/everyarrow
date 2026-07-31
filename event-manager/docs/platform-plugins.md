# Event Manager Platform Plugin Boundary

Event Manager provides the public plugin runtime and the built-in Security enforcement hook. Security and Perch implementations are not included in this repository.

When the internal `security` plugin is staged, it installs authentication, principal-resolution, and authorization providers into `SecurityProviderRegistry`. Without a provider, protected external calls fail closed while public calls and explicitly trusted internal calls remain available.

When the internal `perch` plugin is staged, it registers the Perch lifecycle hook. Without it, Event Manager runs without Perch tracking.

Set `EA_PLUGIN_ROOT` to the directory containing first-level plugin directories. A deployment that requires these plugins should also set:

```text
EA_REQUIRED_PLUGIN_IDS=security,perch
```

`/ready` then reports not ready when either required id is missing or unhealthy. A public or local-only deployment may omit the variable and both plugins.

Private distributions own their plugin source, build, staging, and backend contracts outside this repository.
