#
# Integrates the latest JScholarship and NIHMS package providers into a single, production-ready Deposit Services
# image.  The configuration for Deposit Services is baked into this image.
#
FROM ${docker.deposit-services-core.version}
FROM providers/jscholarship-package-provider:latest AS J10P
FROM providers/nihms-package-provider:latest
COPY --from=J10P /app/BOOT-INF/lib/* /app/BOOT-INF/lib/