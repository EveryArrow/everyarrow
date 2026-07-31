# AWS Utilities

Shared Java helpers for AWS resource operations used by EveryArrow services.
These classes can create or modify cloud resources and must be treated as
privileged infrastructure code.

## Public-Release Safety Rules

- Do not commit real account IDs, role ARNs, resource IDs, private domains, or
  customer identifiers in examples or docs.
- Load credentials through the AWS SDK provider chain or explicitly configured
  private profiles. Do not hardcode credentials.
- Keep secret values out of logs, CLI arguments, and public examples.
- Run destructive or live cloud actions only from private deployment workflows.

`AwsClientBuilderConfigurer` centralizes neutral AWS profile and region selection
for SDK clients. It intentionally contains no resource authorization or lifecycle
policy; callers such as tools, plugins, Event Manager handlers, and pipelines own
those decisions.

## Ingress Defaults

`Ec2Manager.authorizeSecurityGroupIngress(String groupId)` now creates public
HTTP and HTTPS ingress only. SSH, PostgreSQL, and NFS/EFS are sensitive ports
and require explicit scoped ingress:

- SSH: provide an admin CIDR through `authorizeSecurityGroupIngress(..., 22, ...)`.
- PostgreSQL: do not expose publicly; use private CIDRs or security groups.
- EFS/NFS: prefer `authorizeSecurityGroupIngressFromSecurityGroup(..., 2049, ...)`.

The utility rejects `0.0.0.0/0` and `::/0` on SSH (`22`), PostgreSQL (`5432`),
and NFS/EFS (`2049`).

## Key Pair Handling

`Ec2Manager.createKeyPair` returns private key material to the caller but does
not print it. Callers must store the returned value through a private
secret-handling path and rotate any key material previously written to logs,
shell history, chat, tickets, or committed files.

## Public Web Entry Points

ALB/web security groups may intentionally expose HTTP/HTTPS when the entrypoint
is a public web service. Host, database, filesystem, and admin access must be
handled through separate security groups and scoped ingress rules.

## Configuration Examples

Use placeholders in public examples:

```properties
region=us-east-1
resourcePrefix=example
ec2.securityGroupName=example-ec2-sg
ec2.sshIngressCidr=203.0.113.10/32
efs.allowedSecurityGroupId=sg-00000000000000000
acm.primaryDomain=example.com
```

Keep private deployment values in ignored local files such as `env.file` or
`*.private.sql`.
