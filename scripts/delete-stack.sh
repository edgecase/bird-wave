#!/bin/sh

if [ -z "$1" ]; then
    echo "Usage: `basename $0` <stack-name>" >&2
    exit 1
fi

set -e
. `dirname $0`/.demo.env

aws="aws --profile neo"

set -x

$aws cloudformation delete-stack --stack-name "$1"
