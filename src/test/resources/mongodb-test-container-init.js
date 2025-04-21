db = db.getSiblingDB('vms');

db.createUser({
    user: 'oms',
    pwd: 'oms',
    roles: [
        {
            role: 'readWrite',
            db: 'vms'
        },
        {
            role: 'dbAdmin',
            db: 'vms'
        }
    ]
});