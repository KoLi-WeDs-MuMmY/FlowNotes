"""
AudioMemoManager Oracle.
Faithfully mirrors AudioMemoContract and AudioMemoManager logic.
Validates AAC/M4A voice memo recording state, page synchronization, per-stroke audio offset indexing, and playback.
"""

import os
import time
from typing import Optional, Callable, Dict, Any


class AudioMemoManager:
    def __init__(self, base_storage_dir: str):
        self.audio_dir = os.path.join(base_storage_dir, "audio")
        os.makedirs(self.audio_dir, exist_ok=True)
        self.is_recording: bool = False
        self.is_playing: bool = False
        self.current_recording_file: Optional[str] = None
        self.recording_start_time: int = 0
        self.playback_file: Optional[str] = None
        self.playback_position_ms: int = 0
        self.playback_duration_ms: int = 60000

    def start_recording(self, page_id: str) -> Optional[str]:
        self.stop_recording()
        now = int(time.time() * 1000)
        filename = f"memo_{page_id}_{now}.m4a"
        file_path = os.path.join(self.audio_dir, filename)

        try:
            # Create placeholder audio file with standard AAC/M4A header
            with open(file_path, "wb") as f:
                f.write(b"\x00\x00\x00\x20ftypM4A \x00\x00\x00\x00M4A mp42isom\x00\x00\x00\x00")
            self.current_recording_file = file_path
            self.recording_start_time = now
            self.is_recording = True
            return file_path
        except Exception:
            self.is_recording = False
            return None

    def get_current_audio_offset_ms(self) -> int:
        if not self.is_recording:
            return 0
        now = int(time.time() * 1000)
        return max(0, now - self.recording_start_time)

    def stop_recording(self) -> Optional[str]:
        if not self.is_recording:
            return None
        saved = self.current_recording_file
        self.is_recording = False
        return saved

    def play_audio(self, file_path: str,
                   on_progress: Optional[Callable[[int, int], None]] = None,
                   on_complete: Optional[Callable[[], None]] = None) -> bool:
        self.stop_playback()
        if not os.path.exists(file_path):
            return False

        self.playback_file = file_path
        self.is_playing = True
        self.playback_position_ms = 0

        if on_progress:
            on_progress(self.playback_position_ms, self.playback_duration_ms)
        return True

    def seek_to(self, position_ms: int):
        if self.is_playing:
            self.playback_position_ms = max(0, min(position_ms, self.playback_duration_ms))

    def stop_playback(self):
        self.is_playing = False
        self.playback_file = None
        self.playback_position_ms = 0

    def release(self):
        self.stop_recording()
        self.stop_playback()
