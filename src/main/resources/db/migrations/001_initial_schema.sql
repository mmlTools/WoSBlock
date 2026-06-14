CREATE TABLE IF NOT EXISTS wos_islands (
  owner_id CHAR(36) PRIMARY KEY,
  world_name VARCHAR(64) NOT NULL,
  parcel_index INT NOT NULL,
  center_x INT NOT NULL,
  center_y INT NOT NULL,
  center_z INT NOT NULL,
  radius INT NOT NULL,
  expand_north INT NOT NULL,
  expand_south INT NOT NULL,
  expand_east INT NOT NULL,
  expand_west INT NOT NULL,
  visit_mode VARCHAR(24) NOT NULL,
  generator_level INT NOT NULL,
  generator_xp BIGINT NOT NULL,
  achievement_level INT NOT NULL,
  waypoints TEXT NOT NULL,
  trusted_members TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS wos_balances (
  player_id CHAR(36) NOT NULL,
  world_name VARCHAR(64) NOT NULL,
  balance DOUBLE NOT NULL,
  PRIMARY KEY (player_id, world_name)
);

CREATE TABLE IF NOT EXISTS wos_quest_completions (
  player_id CHAR(36) NOT NULL,
  quest_id VARCHAR(128) NOT NULL,
  completed_at BIGINT NOT NULL,
  PRIMARY KEY (player_id, quest_id)
);

CREATE TABLE IF NOT EXISTS wos_quest_progress (
  player_id CHAR(36) NOT NULL,
  quest_id VARCHAR(128) NOT NULL,
  progress INT NOT NULL,
  PRIMARY KEY (player_id, quest_id)
);

CREATE TABLE IF NOT EXISTS wos_auction_listings (
  listing_id CHAR(36) PRIMARY KEY,
  seller_id CHAR(36) NOT NULL,
  seller_name VARCHAR(64) NOT NULL,
  item_blob LONGBLOB NOT NULL,
  price DOUBLE NOT NULL,
  created_at BIGINT NOT NULL
);
