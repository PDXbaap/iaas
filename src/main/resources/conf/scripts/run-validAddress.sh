#/bin/bash

RESULT=`sh run-createCA-tcadm.sh`
ADDRESS=`echo $RESULT | cut -d ':' -f 4 | sed 's/ //g'`

while [ -z "$ADDRESS" ]; do
	RESULT=`sh run-createCA-tcadm.sh`
	ADDRESS=`echo $RESULT | cut -d ':' -f 4`
done

echo $RESULT





