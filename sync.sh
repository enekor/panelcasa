#!/bin/sh
# Copia el código de la carpeta de trabajo al repositorio del contenedor.
set -e
cd "$(dirname "$0")"
docker cp ./. 3b42393586e1:/root/panelcasa/
docker exec 3b42393586e1 rm -f /root/panelcasa/sync.sh
