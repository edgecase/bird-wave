# bird-wave


## Getting Started

1. Launch a repl
2. (configure)
3. (go)

This launches the web server and an in-memory datomic transactor. A small
seed file is also loaded.

## Getting More Data

You have a couple of options here. You can run the import job on the full
eBird.txt file, but that will take somewhere between 3-5 hours.

Unless you've made schema changes, a quicker way to load data is to restore from
a backup.

`$ bin/datomic restore-db s3://birdman.neo.com/backups/2014/02 datomic:dev://localhost:4334/birdman`

## Backing Up

`$ bin/datomic backup-db datomic:dev://localhost:4334/birdman s3://birdman.neo.com/backups/2014/02`

## Deployment

```bash
$ lein do cljsbuild clean, cljsbuild once prod, pedestal uberwar
$ ./scripts/update-stack.sh $PWD/target/bird-wave-0.1.0-SNAPSHOT-standalone.war birdwave-production
```

