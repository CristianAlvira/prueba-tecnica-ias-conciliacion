#!/bin/bash
set -euo pipefail

SCRIPTS=(01_schema.sql 02_sample_data.sql 04_reconcile_payment.sql 05_optional_constraints.sql)

for script in "${SCRIPTS[@]}"; do
  if [[ -f "/opt/db/${script}" ]]; then
    echo "Cargando ${script}"
    sqlplus -s -L "${APP_USER}/${APP_USER_PASSWORD}@//localhost/FREEPDB1" @"/opt/db/${script}" < /dev/null
  fi
done
