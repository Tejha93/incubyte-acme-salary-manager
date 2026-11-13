#!/bin/sh
set -eu

# A host-mounted volume can mask the ownership set in the image.
chown acme:acme /var/data
exec runuser -u acme -- java -jar /app/salary-manager.jar
