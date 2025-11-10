# Local development using PubSub

PubSub commons supports using the PubSub emulator for local development. To enable this, some things need to be taken
care of:

- starting the emulator
- creating the topic structure
- creating a tenant that has the pubsub messaging type
- making sure no other communication disturbs the tests

## Starting the Emulator
To start the emulator, run

```
gcloud beta emulators pubsub start --project=local-test-project
```

## Creating the topic structure
After starting the emulator, some topics have to be created. This can be done by running PubSubEmulatorSetup.

## Creating a tenant that uses Pub/Sub messaging
When creating or updating the tenant, make sure to activate PubSub communication.
```json
{
  "enabled": true,
  "ext": {
    "messaging-type": "pubsub"
  }
}
```

## Disable concurrent messaging
When using the emulator while running quarkus:dev, it is recommended to disable kafka and ampq messaging. This ensures
that pubsub is actually used.

```shell
mvn quarkus:dev
-Dhono.kafka-messaging.disabled=true
-Dhono.kafka-messaging.disabled=true
```

## Connecting an example client to MQTT
make sure the client subscribes to`c///q/#` to receive commands.


## Additional thoughts

- Make sure when running quarkus:dev to manage the quarkus.http.port for the services and avoid collisions 
- Make sure that the corresponding databases/caches are running, e.g. a device registry postgres or an infinispan
