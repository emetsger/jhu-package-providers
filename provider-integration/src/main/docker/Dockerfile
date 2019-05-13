#
# Integrates the latest JScholarship and NIHMS package providers into a single, production-ready Deposit Services
# image.  The configuration for Deposit Services is baked into this image.
#
FROM providers/jscholarship-package-provider:latest AS J10P
FROM providers/nihms-package-provider:latest AS NIHMS
FROM providers/bagit-package-provider:latest AS BAGIT
FROM ${docker.deposit-services-core.version}

LABEL version=${project.parent.version}
LABEL commit=${git.commit.id.abbrev}
LABEL commitdate=${git.commit.time}
LABEL builddate=${git.build.time}
LABEL dirty=${git.dirty}

LABEL depositservices-image-version=${docker.deposit-services-core.version}
LABEL depositservices-java-version=${deposit-services.version}

COPY --from=J10P /provider-with-dependencies/* /app/BOOT-INF/lib/
COPY --from=NIHMS /provider-with-dependencies/* /app/BOOT-INF/lib/
COPY --from=BAGIT /provider-with-dependencies/* /app/BOOT-INF/lib/