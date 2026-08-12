SET DATABASE SQL SYNTAX MYS TRUE;
INSERT IGNORE INTO users (ID, USER_NAME, GIVEN_NAME, FAMILY_NAME, ACTIVE, DISPLAY_NAME) VALUES
  ('7510949d-afbd-4976-90f4-47f1b8a1e503', 'Aaron.Nichols', 'Aaron', 'Nichols',1, 'Aaron Nichols'),
  ('4f5fc6f8-719e-43a6-89e1-bbdd6e639775', 'Adam.Kennedy', 'Adam', 'Kennedy',1, 'Adam Kennedy'),
  ('13339394-d287-4599-ba34-a80d1b2730b0', 'Amanda.Ross', 'Amanda', 'Ross',1, 'Amanda Ross');

INSERT INTO user_emails (USER_ID, VALUE, TYPE, IS_PRIMARY)
SELECT u.ID, 'aaron.nichols@sailpointdemo.com', 'work', 1
  FROM users u
 WHERE u.ID = '7510949d-afbd-4976-90f4-47f1b8a1e503'
   AND NOT EXISTS (SELECT 1 FROM user_emails e WHERE e.USER_ID = u.ID);

INSERT INTO user_emails (USER_ID, VALUE, TYPE, IS_PRIMARY)
SELECT u.ID, 'adam.kennedy@sailpointdemo.com', 'work', 1
  FROM users u
 WHERE u.ID = '4f5fc6f8-719e-43a6-89e1-bbdd6e639775'
   AND NOT EXISTS (SELECT 1 FROM user_emails e WHERE e.USER_ID = u.ID);

INSERT INTO user_emails (USER_ID, VALUE, TYPE, IS_PRIMARY)
SELECT u.ID, 'amanda.ross@sailpointdemo.com', 'work', 1
  FROM users u
 WHERE u.ID = '13339394-d287-4599-ba34-a80d1b2730b0'
   AND NOT EXISTS (SELECT 1 FROM user_emails e WHERE e.USER_ID = u.ID);

INSERT IGNORE INTO groups (ID, DISPLAY_NAME) VALUES
  ('0e4f0c3e-5310-4be9-9955-02439db77bfa', 'Group 1'),
  ('a4415829-d383-4142-856b-c265e7ebf6d4', 'Group 2'),
  ('282bc3e7-db3a-4c70-9221-31422fd344a0', 'Group 3');

INSERT IGNORE INTO GROUPMEMBERSHIPS (ID, GROUP_ID, USER_ID, GROUP_DISPLAY, USER_DISPLAY) VALUES
  ('851a6482-8ec3-456a-8243-0e3682a934a5', '0e4f0c3e-5310-4be9-9955-02439db77bfa', '7510949d-afbd-4976-90f4-47f1b8a1e503', 'Group 1', 'Aaron Nichols'),
  ('75e72f77-f325-4db5-8197-0330ea1a4115', 'a4415829-d383-4142-856b-c265e7ebf6d4', '4f5fc6f8-719e-43a6-89e1-bbdd6e639775', 'Group 2', 'Adam Kennedy'),
  ('9a14737c-2baa-4317-b30a-6907c03004a2', '282bc3e7-db3a-4c70-9221-31422fd344a0', '13339394-d287-4599-ba34-a80d1b2730b0', 'Group 3', 'Amanda Ross');

UPDATE GROUPMEMBERSHIPS SET GROUP_DISPLAY = 'Group 1', USER_DISPLAY = 'Aaron Nichols'
  WHERE ID = '851a6482-8ec3-456a-8243-0e3682a934a5';
UPDATE GROUPMEMBERSHIPS SET GROUP_DISPLAY = 'Group 2', USER_DISPLAY = 'Adam Kennedy'
  WHERE ID = '75e72f77-f325-4db5-8197-0330ea1a4115';
UPDATE GROUPMEMBERSHIPS SET GROUP_DISPLAY = 'Group 3', USER_DISPLAY = 'Amanda Ross'
  WHERE ID = '9a14737c-2baa-4317-b30a-6907c03004a2';
