//Creates users required for the device registry to connect to the mongo db database
db.createUser(
    {
        user: "${hono.mongodb.username}",
        pwd: "${hono.mongodb.password}",
        roles:[
            {
                role: "readWrite",
                db:   "${hono.mongodb.database.name}"
            }
        ]
    }
);
