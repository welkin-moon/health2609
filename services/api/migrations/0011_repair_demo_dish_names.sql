-- Repair demo dish names if an earlier remote seed was applied through a non-UTF-8 shell.
UPDATE dishes SET name = '宫保鸡丁' WHERE id IN ('demo-dish-20260929-kungpao', 'demo-dish-today-kungpao');
UPDATE dishes SET name = '清炒时蔬' WHERE id IN ('demo-dish-20260929-greens', 'demo-dish-today-greens');
UPDATE dishes SET name = '紫菜蛋花汤' WHERE id = 'demo-dish-20260929-soup';
UPDATE dishes SET name = '五谷米饭' WHERE id IN ('demo-dish-20260929-rice', 'demo-dish-today-rice');
