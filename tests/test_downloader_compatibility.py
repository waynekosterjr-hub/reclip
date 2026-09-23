"""Offline integration contracts for the pinned yt-dlp dependency.

Run with: python -m unittest discover -s tests -v
These validate extractor/encoder option wiring, not live website availability.
"""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import yt_dlp

ENGINE = Path(__file__).resolve().parents[1] / 'app/src/main/python/reclip_engine.py'
spec = importlib.util.spec_from_file_location('reclip_engine', ENGINE)
engine = importlib.util.module_from_spec(spec)
spec.loader.exec_module(engine)


class DownloaderCompatibility(unittest.TestCase):
    def exercise_download(self, mode, profile=None, format_id=None, lame=True):
        captured = {}
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / 'fixture.media'
            output.write_bytes(b'offline fixture')

            class OfflineDownloader(yt_dlp.YoutubeDL):
                def __init__(self, options):
                    captured.update(options)
                    # Use the real updated option parser and postprocessor classes.
                    super().__init__(options, auto_init=False)

                def extract_info(self, url, download=True):
                    return {'title': 'fixture', 'id': 'fixture', 'ext': 'media'}

                def prepare_filename(self, info, *args, **kwargs):
                    return str(output)

            with patch.object(engine.yt_dlp, 'YoutubeDL', OfflineDownloader), \
                 patch.object(engine, '_check_libmp3lame', return_value=lame), \
                 patch.object(engine, '_get_ffmpeg_opts', return_value={}), \
                 patch.object(engine, '_download_thumbnail', return_value=''):
                result = json.loads(engine.download_media(
                    'https://example.com/fixture', directory, mode, format_id,
                    'fixture', profile))
            self.assertTrue(result['success'], result)
        return captured

    def test_audio_profiles_survive_new_downloader(self):
        cases = [
            ('mp3_320_cbr', 'mp3', '320', '48000'),
            ('mp3_v0_vbr', 'mp3', '0', '44100'),
            ('mp3_256_cbr', 'mp3', '256', '44100'),
            ('flac_lossless', 'flac', None, '48000'),
        ]
        for profile, codec, quality, rate in cases:
            with self.subTest(profile=profile):
                opts = self.exercise_download('audio', profile)
                self.assertEqual(opts['format'], 'bestaudio/best')
                processor = opts['postprocessors'][0]
                self.assertEqual(processor['preferredcodec'], codec)
                self.assertEqual(processor.get('preferredquality'), quality)
                self.assertIn(rate, opts['postprocessor_args']['ffmpegextractaudio'])
                self.assertEqual([p['key'] for p in opts['postprocessors']],
                                 ['FFmpegExtractAudio', 'FFmpegMetadata', 'EmbedThumbnail'])
                self.assertNotIn('merge_output_format', opts)

    def test_mp3_without_lame_retains_aac_fallback(self):
        opts = self.exercise_download('audio', 'mp3_320_cbr', lame=False)
        self.assertEqual(opts['postprocessors'][0],
                         {'key': 'FFmpegExtractAudio', 'preferredcodec': 'aac',
                          'preferredquality': '192'})
        self.assertNotIn('postprocessor_args', opts)

    def test_video_selection_and_merge_survive(self):
        for format_id, selector in [(None, 'bestvideo+bestaudio/best'),
                                    ('137', '137+bestaudio/best')]:
            with self.subTest(format_id=format_id):
                opts = self.exercise_download('video', format_id=format_id)
                self.assertEqual(opts['format'], selector)
                self.assertEqual(opts['merge_output_format'], 'mp4')
                self.assertEqual([p['key'] for p in opts['postprocessors']],
                                 ['FFmpegMetadata', 'EmbedThumbnail'])

    def test_spotify_still_uses_existing_adapter(self):
        # Updating yt-dlp must not silently change Spotify encoding behavior.
        with patch.object(engine, '_download_spotify_media', return_value='sentinel') as call:
            self.assertEqual(engine.download_media('https://open.spotify.com/track/test',
                                                  'unused', 'audio', audio_profile='flac_lossless'),
                             'sentinel')
            call.assert_called_once_with('https://open.spotify.com/track/test', 'unused')

    def test_dependency_version(self):
        self.assertEqual(yt_dlp.version.__version__, '2026.08.19')


if __name__ == '__main__':
    unittest.main()
