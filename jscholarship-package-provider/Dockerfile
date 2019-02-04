#
# Produces a production-ready Deposit Services image containing the JScholarship package provider.  Deposit Services
# configuration must be provided at runtime.
#
FROM ${docker.deposit-services-core.version}

ADD target/jscholarship-package-provider-${project.version}.jar BOOT-INF/lib
ADD target/dependencies/ BOOT-INF/lib