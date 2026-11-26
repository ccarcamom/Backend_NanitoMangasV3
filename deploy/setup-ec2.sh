#!/usr/bin/env bash
# Prepara una instancia EC2 con Amazon Linux 2023: Docker, plugin Compose, git y 2 GB de swap.
# Uso (como ec2-user): bash deploy/setup-ec2.sh   y luego cerrar sesión y volver a entrar.
set -eu
sudo dnf install -y docker git
sudo systemctl enable --now docker
sudo usermod -aG docker "$USER"

ARCH="$(uname -m)"   # x86_64 o aarch64
sudo mkdir -p /usr/local/lib/docker/cli-plugins
sudo curl -fsSL "https://github.com/docker/compose/releases/latest/download/docker-compose-linux-${ARCH}" \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

# Compose recientes exigen buildx >= 0.17 para compilar imágenes; el de Amazon Linux es más viejo o no existe.
BX_ARCH="amd64"; [ "$ARCH" = "aarch64" ] && BX_ARCH="arm64"
BX_VER="$(curl -fsSLI -o /dev/null -w '%{url_effective}' https://github.com/docker/buildx/releases/latest | grep -o 'v[0-9.]*$')"
sudo curl -fsSL "https://github.com/docker/buildx/releases/download/${BX_VER}/buildx-${BX_VER}.linux-${BX_ARCH}" \
  -o /usr/local/lib/docker/cli-plugins/docker-buildx
sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-buildx

# Swap de 2 GB: evita que la compilación de Maven mate procesos si falta memoria.
if ! swapon --show | grep -q /swapfile; then
  sudo dd if=/dev/zero of=/swapfile bs=1M count=2048 status=none   # dd y no fallocate: XFS rechaza swap con huecos
  sudo chmod 600 /swapfile
  sudo mkswap /swapfile >/dev/null
  sudo swapon /swapfile
  grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab >/dev/null
fi

echo "Listo. Cierra la sesión SSH y vuelve a entrar para que el grupo docker tenga efecto."
