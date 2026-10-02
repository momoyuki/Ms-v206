#!/bin/sh
set -eu

for file in \
  '1 - InitTables_characters.sql' \
  '2 - InitTables_drops.sql' \
  '3 - InitTables_cashshop.sql' \
  '4 - drops.sql' \
  '5 - InitTable_equip_drops.sql' \
  '6 - beautyalbum.sql' \
  '7 - charactercard.sql' \
  '8 - InitTable_npc.sql' \
  '9 - InitTables_MonsterCollection.sql' \
  '10 - InitTables_shops.sql' \
  'hairequips.sql' \
  'unseenequips.sql'
do
  mysql --protocol=socket -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" < "/sql/$file"
done

touch /var/lib/mysql/.ms-v206-init-complete
