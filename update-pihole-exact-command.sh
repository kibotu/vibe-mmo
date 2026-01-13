#!/bin/bash
# Exact Pi-hole update command based on your current configuration

# Step 1: Pull the latest image (while Pi-hole is still running to avoid DNS issues)
echo "Pulling latest Pi-hole image..."
sudo docker pull pihole/pihole:latest

# Step 2: Stop and remove the old container
echo "Stopping current container..."
sudo docker stop pihole || echo "Container already stopped or doesn't exist"
echo "Removing old container..."
sudo docker rm pihole || echo "Container already removed or doesn't exist"

# Step 3: Recreate the container with the exact same configuration
echo "Creating new container with updated image..."
sudo docker run -d \
  --name pihole \
  --hostname pi.hole \
  -p 53:53/tcp \
  -p 53:53/udp \
  -p 67:67/udp \
  -p 8080:80/tcp \
  -e TZ='Germany/Berlin' \
  -e VIRTUAL_HOST='pi.hole' \
  -e PROXY_LOCATION='pi.hole' \
  -e FTLCONF_LOCAL_IPV4='127.0.0.1' \
  -e IPv6='True' \
  -e S6_KEEP_ENV='1' \
  -e S6_BEHAVIOUR_IF_STAGE2_FAILS='2' \
  -e S6_CMD_WAIT_FOR_SERVICES_MAXTIME='0' \
  -e FTL_CMD='no-daemon' \
  -e DNSMASQ_USER='pihole' \
  -v /home/kibotu/Documents/etc-pihole:/etc/pihole \
  -v /home/kibotu/Documents/etc-dnsmasq.d:/etc/dnsmasq.d \
  --dns=127.0.0.1 \
  --dns=1.1.1.1 \
  --restart=unless-stopped \
  pihole/pihole:latest

# Step 4: Verify the update
echo ""
echo "Container created. Checking status..."
sudo docker ps | grep pihole
echo ""
echo "Checking logs..."
sudo docker logs --tail 20 pihole
echo ""
echo "Update complete! Check the web interface at http://your-pi-ip:8080/admin"

