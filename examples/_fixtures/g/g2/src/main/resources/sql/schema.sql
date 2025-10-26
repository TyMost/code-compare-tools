CREATE TABLE gauss_customer (
    customer_id varchar(32) PRIMARY KEY,
    tier varchar(16),
    anniversary_tag varchar(32) DEFAULT NULL
);
