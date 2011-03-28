alter table ACT_CY_CONFIG alter VALUE_ clob;

create table ACT_CY_PROCESS_SOLUTION (
	ID_ NVARCHAR2(128) NOT NULL,
	LABEL_ NVARCHAR2(255) NOT NULL,
	STATE_ NVARCHAR2(32) NOT NULL,
	primary key(ID_)
);

create table ACT_CY_V_FOLDER (
	ID_ NVARCHAR2(128) NOT NULL,
	LABEL_ NVARCHAR2(255) NOT NULL,
	CONNECTOR_ID_ NVARCHAR2(128) NOT NULL,
	REFERENCED_NODE_ID_ NVARCHAR2(550) NOT NULL,
	PROCESS_SOLUTION_ID_ NVARCHAR2(128) NOT NULL,
	TYPE_ NVARCHAR2(32) NOT NULL,
	primary key(ID_)
);

create index ACT_CY_IDX_V_FOLDER on ACT_CY_V_FOLDER(PROCESS_SOLUTION_ID_);
alter table ACT_CY_V_FOLDER 
    add constraint FK_CY_PROCESS_SOLUTION 
    foreign key (PROCESS_SOLUTION_ID_) 
    references ACT_CY_PROCESS_SOLUTION (ID_);