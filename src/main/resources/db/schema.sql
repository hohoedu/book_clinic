-- DROP (FK 역순)
-- DROP (FK 역순)
IF OBJECT_ID('erp_bookstore_code',         'U') IS NOT NULL DROP TABLE erp_bookstore_code;
IF OBJECT_ID('erp_bookstore_itempool_del', 'U') IS NOT NULL DROP TABLE erp_bookstore_itempool_del;
IF OBJECT_ID('erp_bookstore_itempool',     'U') IS NOT NULL DROP TABLE erp_bookstore_itempool;
IF OBJECT_ID('erp_bookstore_item_del',     'U') IS NOT NULL DROP TABLE erp_bookstore_item_del;
IF OBJECT_ID('erp_bookstore_item_center',  'U') IS NOT NULL DROP TABLE erp_bookstore_item_center;
IF OBJECT_ID('erp_bookstore_item',         'U') IS NOT NULL DROP TABLE erp_bookstore_item;
IF OBJECT_ID('erp_bookstore_content_del',  'U') IS NOT NULL DROP TABLE erp_bookstore_content_del;
IF OBJECT_ID('erp_bookstore_content',      'U') IS NOT NULL DROP TABLE erp_bookstore_content;
IF OBJECT_ID('erp_student',                'U') IS NOT NULL DROP TABLE erp_student;
IF OBJECT_ID('erp_user',                   'U') IS NOT NULL DROP TABLE erp_user;
IF OBJECT_ID('erp_center',                 'U') IS NOT NULL DROP TABLE erp_center;


-- CREATE
CREATE TABLE erp_bookstore_code (
    gubun          VARCHAR(1) NOT NULL,
    code           VARCHAR(2)  NOT NULL,
    codeNm         VARCHAR(20) NOT NULL
    CONSTRAINT PK_erp_bookstore_code PRIMARY KEY CLUSTERED (gubun ASC, code ASC)
    WITH (
        PAD_INDEX = OFF, 
        STATISTICS_NORECOMPUTE = OFF, 
        IGNORE_DUP_KEY = OFF, 
        ALLOW_ROW_LOCKS = ON, 
        ALLOW_PAGE_LOCKS = ON
        ) ON [PRIMARY])
    ON [PRIMARY];

CREATE TABLE erp_center (
    id              INT IDENTITY(1,1) PRIMARY KEY,
    opened_at       DATE,
    biz_no          VARCHAR(20),
    center_code     VARCHAR(20)  NOT NULL UNIQUE,
    region_key      VARCHAR(20),
    center_tel      VARCHAR(20),
    director_name   VARCHAR(50),
    center_address  VARCHAR(255),
    center_email    VARCHAR(100),
    center_name     VARCHAR(100),
    status          VARCHAR(20),
    manager_name    VARCHAR(50),
    manager_tel     VARCHAR(20),
    manager_email   VARCHAR(100),
    registration_no VARCHAR(50),
    biz_name        VARCHAR(100)
);

CREATE TABLE erp_user (
    id            INT IDENTITY(1,1) PRIMARY KEY,
    created_at    DATETIME2    DEFAULT CURRENT_TIMESTAMP,
    center_code   VARCHAR(50),
    role_key      VARCHAR(50),
    user_code     VARCHAR(50),
    user_id       VARCHAR(100) NOT NULL,
    user_name     VARCHAR(100),
    password_hash VARCHAR(255),
    salt          VARCHAR(100),
    type          VARCHAR(20),
    user_phone    VARCHAR(20),
    is_han        BIT          DEFAULT 0,
    is_book       BIT          DEFAULT 0,
    use_yn        BIT          DEFAULT 1,
    is_clinic     BIT          DEFAULT 0
);

CREATE TABLE erp_student (
    id                    INT IDENTITY(1,1) PRIMARY KEY,
    created_at            DATETIME2   DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME2   DEFAULT CURRENT_TIMESTAMP,
    center_code           VARCHAR(50),
    grade_key             VARCHAR(50),
    status_key            VARCHAR(50),
    school                VARCHAR(100),
    student_id            VARCHAR(100),
    student_name          VARCHAR(100),
    address               VARCHAR(255),
    address_detail        VARCHAR(255),
    app_id                VARCHAR(100),
    app_password          VARCHAR(255),
    app_token             VARCHAR(255),
    birth                 VARCHAR(20),
    profile_img           VARCHAR(255),
    consult_key           VARCHAR(50),
    billing_phone         VARCHAR(20),
    serial_num            VARCHAR(100),
    gender                BIT         DEFAULT 0,
    student_privacy_agree BIT         DEFAULT 0,
    is_hoho               BIT         DEFAULT 0,
    sub_han               BIT         DEFAULT 0,
    sub_book              BIT         DEFAULT 0,
    sub_hoho              BIT         DEFAULT 0
);

CREATE TABLE erp_bookstore_content (
    content_id     INT IDENTITY(1,1) PRIMARY KEY,
    original_title VARCHAR(255),
    author         VARCHAR(100),
    genre          VARCHAR(50),
    content_type   VARCHAR(50),
    schoolyear     VARCHAR(20),
    summary        VARCHAR(2000),
    keywords       VARCHAR(1000)
);

CREATE TABLE erp_bookstore_content_del (
    del_id         INT IDENTITY(1,1) PRIMARY KEY,
    deleted_at     DATETIME2    DEFAULT CURRENT_TIMESTAMP,
    deleted_by     VARCHAR(100),
    content_id     INT,
    original_title VARCHAR(255),
    author         VARCHAR(100),
    genre          VARCHAR(50),
    content_type   VARCHAR(50),
    schoolyear     VARCHAR(20),
    summary        VARCHAR(2000),
    keywords       VARCHAR(1000)
);

CREATE TABLE erp_bookstore_item (
    bcode          VARCHAR(50)  PRIMARY KEY,
    content_id     INT,
    book_title     VARCHAR(255),
    publisher      VARCHAR(100),
    keywords       VARCHAR(500),
    FOREIGN KEY (content_id) REFERENCES erp_bookstore_content(content_id)
);

CREATE TABLE erp_bookstore_item_center (
    bcode          VARCHAR(50)  NOT NULL,
    center_code    VARCHAR(20)  NOT NULL,
    quantity       INT          NOT NULL DEFAULT 0,
    state          VARCHAR(20),
    registered_at  DATETIME2    DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (bcode, center_code),
    FOREIGN KEY (bcode)        REFERENCES erp_bookstore_item(bcode),
    FOREIGN KEY (center_code)  REFERENCES erp_center(center_code)
);

CREATE TABLE erp_bookstore_item_del (
    del_id         INT IDENTITY(1,1) PRIMARY KEY,
    deleted_at     DATETIME2    DEFAULT CURRENT_TIMESTAMP,
    deleted_by     VARCHAR(100),
    bcode          VARCHAR(50),
    content_id     INT,
    book_title     VARCHAR(255),
    publisher      VARCHAR(100),
    keywords       VARCHAR(500)
);

CREATE TABLE erp_bookstore_itempool (
    content_id     INT,
    qnum           VARCHAR(20),
    q              NVARCHAR(2000),
    qex            NVARCHAR(2000),
    e1             NVARCHAR(500),
    e2             NVARCHAR(500),
    e3             NVARCHAR(500),
    e4             NVARCHAR(500),
    ans            VARCHAR(100),
    qtype          VARCHAR(20),
    qexgb          VARCHAR(20),
    state          VARCHAR(20),
    PRIMARY KEY (content_id, qnum),
    FOREIGN KEY (content_id) REFERENCES erp_bookstore_content(content_id)
);

CREATE TABLE erp_bookstore_itempool_del (
    del_id         INT IDENTITY(1,1) PRIMARY KEY,
    deleted_at     DATETIME2    DEFAULT CURRENT_TIMESTAMP,
    deleted_by     VARCHAR(100),
    content_id     INT,
    qnum           VARCHAR(20),
    q              NVARCHAR(2000),
    qex            NVARCHAR(2000),
    e1             NVARCHAR(500),
    e2             NVARCHAR(500),
    e3             NVARCHAR(500),
    e4             NVARCHAR(500),
    ans            VARCHAR(100),
    qtype          VARCHAR(20),
    qexgb          VARCHAR(20),
    state          VARCHAR(20)
);
