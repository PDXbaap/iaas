#/bin/bash

# Create TCadm CA Certifacate Keypair
openssl ecparam -genkey -name secp256k1 -out ./demoCA/private/tcadm.key

# Create TCadm CA Certifacate Request
openssl req -new -days 365 -key ./demoCA/private/tcadm.key -out tcadm.csr -subj "/C=CN/ST=Beijing/L=Haidian/O=PDX/OU=PDX/CN=CA-TCadm"

# Create TCadm CA Certifacate By Sign Request
openssl ca  -in tcadm.csr -out tcadm.crt -days 3650 -cert trust-signer.crt -keyfile ./demoCA/private/trust-signer.key -passin pass:123456 -o

# Create Eth Keypair
ORIGINAL=`openssl ec -in ./demoCA/private/tcadm.key -text -noout`
COMPRESS=`openssl ec -conv_form compressed -in ./demoCA/private/tcadm.key -text -noout`


ORIGINAL_TRIM=`echo $ORIGINAL | sed 's/://g' | sed 's/00//g' | sed 's/04//g'`
PUBLICKEY_UNCOMPRESS=`echo ${ORIGINAL_TRIM:98:132} | sed 's/ //g'`
COMPRESS_TRIM=`echo $COMPRESS | sed 's/://g' | sed 's/00//g' | sed 's/04//g'`
ADDRESS=`echo -n $PUBLICKEY_UNCOMPRESS | keccak-256sum -x -l | tr -d ' -' | tail -c 41`

# Test Print
#echo '----------------'
#echo $PUBLICKEY_UNCOMPRESS
#echo $ORIGINAL_TRIM
#echo '----------------'
#echo $COMPRESS_TRIM
#echo '----------------'

echo 'privateKey:' ${ORIGINAL_TRIM:27:67} | sed 's/ //g'
echo 'publicKey:' ${COMPRESS_TRIM:98:69} | sed 's/ //g'
echo 'address:' ${ADDRESS}
