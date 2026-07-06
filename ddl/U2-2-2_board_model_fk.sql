-- U2-2-2 · bios_setting_template.board_key(varchar) → board_model FK 전환 (사용자 지시 2026-07-05)
-- 기존 행은 board_key == board_model.model_name 사상으로 이관 (BoardBIOS 선례와 동일: ON DELETE CASCADE)
ALTER TABLE bios_setting_template ADD COLUMN board_model_id BIGINT NULL AFTER description;

UPDATE bios_setting_template t
  JOIN board_model b ON b.model_name = t.board_key
   SET t.board_model_id = b.id;

-- 매칭 실패 행이 있으면 여기서 중단해 수동 판단 (0 이어야 함)
SELECT COUNT(*) AS unmatched FROM bios_setting_template WHERE board_model_id IS NULL;

ALTER TABLE bios_setting_template
  MODIFY board_model_id BIGINT NOT NULL,
  ADD CONSTRAINT fk_bios_setting_template_board_model
    FOREIGN KEY (board_model_id) REFERENCES board_model (id) ON DELETE CASCADE;

ALTER TABLE bios_setting_template DROP COLUMN board_key;
