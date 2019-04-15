#/bin/bash

# Create Trust-Signer-CA Certifacate Keypair
openssl genrsa -des3 -out ./demoCA/private/trust-signer.key 2048

# Create Trust-Signer-CA Certifacate Request
openssl req -new -days 365 -key ./demoCA/private/trust-signer.key -out trust-signer.csr -subj "/C=CN/ST=Beijing/L=Haidian/O=PDX/OU=PDX/CN=CA-TrustSigner/emaliAddress=pdx@123.com"

# Create Trust-Signer-CA Certifacate By Sign Request
openssl ca  -in trust-signer.csr -out trust-signer.crt -days 3650 -cert ca.crt -keyfile ./demoCA/private/cakey.pem


