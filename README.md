# floodgate-limbo
Compatibility for floodgate's send data proxy config option

**Requires at least Limbo version 0.7.2-ALPHA**

## Limbo
https://github.com/LOOHP/Limbo/

## Downloads
- [Jenkins](https://ci.loohpjames.com/job/Floodgate-Limbo/)

## Installation
1. Install Floodgate-Limbo on the your Limbo server
2. Enable Bungeecord mode in your `server.properties`
3. Start the Limbo server.
4. Copy the key.pem file in the proxy Floodgate config folder to the Limbo server’s Floodgate config folder. **DO NOT DISTRIBUTE THIS KEY TO ANYBODY!** This key is what allows for Bedrock accounts to bypass the Java Edition authentication, and if anyone gets ahold of this, they can wreak havoc on your server.
5. Restart the Limbo server.
