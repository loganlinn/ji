(ns ji.reader
  "Registers tag parsers for reader"
  (:require [cljs.reader :refer [register-tag-parser!]]
            [ji.domain.game :as game]
            [ji.domain.player :as player]
            [ji.domain.messages :as msg]))

(defn register-tag-parsers! []
  (register-tag-parser! 'ji.domain.game.Game game/map->Game)
  (register-tag-parser! 'ji.domain.player.Player player/map->Player)
  (register-tag-parser! 'ji.domain.messages.ErrorMessage msg/map->ErrorMessage)
  (register-tag-parser! 'ji.domain.messages.GameLeaveMessage msg/map->GameLeaveMessage)
  (register-tag-parser! 'ji.domain.messages.GameStateMessage msg/map->GameStateMessage)
  (register-tag-parser! 'ji.domain.messages.GameFinishMessage msg/map->GameFinishMessage)
  ;(register-tag-parser! 'ji.domain.messages.GameControlMessage msg/map->GameControlMessage)
  )
