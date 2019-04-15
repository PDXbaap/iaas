#/bin/bash

# Create Root CA Certifacate Keypair
openssl genrsa -des3 -out ./demoCA/private/cakey.pem 2048

# Create Root CA Certifacate Request
openssl req -new -days 365 -key ./demoCA/private/cakey.pem -out ca.csr -subj "/C=CN/ST=Beijing/L=Haidian/O=PDX/OU=PDX/CN=CA-Root/emaliAddress=pdx@123.com"

# Create Root CA Certifacate By Sign Request 
openssl ca -selfsign -in ca.csr -out ca.crt


