#!/bin/sh

if [ -z "$1" -o -z "$2" ]; then
    echo "Usage: `basename $0` <path/to/uber.war> <stack-name>" >&2
    exit 1
fi

set -e
. `dirname $0`/.demo.env

aws="aws --profile neo"

# Uploaded war file will have modifcation ts embedded in its name
warfile=uberwars/`basename $1 .war`-`stat -f %m $1`.war
remotefile="s3://$SRCBUCKET/$warfile"

if [ -z "$($aws s3 ls $remotefile)" ]; then
    $aws s3 cp "$1" $remotefile
else
    echo "$remotefile exists in S3. Skipping upload."
fi

parameters="--parameters ParameterKey=SrcBucket,ParameterValue=$SRCBUCKET"
parameters="$parameters ParameterKey=WarFile,ParameterValue=$warfile"
parameters="$parameters ParameterKey=TransactorLogBucket,ParameterValue=$LOGBUCKET"
parameters="$parameters ParameterKey=SshKey,ParameterValue=$SSHKEY"
parameters="$parameters ParameterKey=MyDatomicUserName,ParameterValue=$MY_DATOMIC_USERNAME"
parameters="$parameters ParameterKey=MyDatomicPassword,ParameterValue=$MY_DATOMIC_PASSWORD"

set -x
$aws cloudformation create-stack \
    --stack-name "$2" \
    --template-body file://`dirname $0`/../config/cfn-template.json \
    --capabilities CAPABILITY_IAM \
    $parameters
