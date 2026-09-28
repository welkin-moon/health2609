INSERT OR IGNORE INTO schools (
  id,
  name,
  timezone,
  daily_activity_target_minutes
) VALUES (
  'demo-school',
  '健康生活示范学校',
  'Asia/Shanghai',
  120
);

INSERT OR IGNORE INTO class_groups (
  id,
  school_id,
  name
) VALUES (
  'demo-class',
  'demo-school',
  '高二（4）班'
);

INSERT OR IGNORE INTO student_memberships (
  id,
  school_id,
  class_group_id,
  participant_id
) VALUES (
  'demo-membership',
  'demo-school',
  'demo-class',
  'demo-student'
);
