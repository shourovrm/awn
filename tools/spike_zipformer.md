# Spike: Zipformer token timestamps

Model `zipformer_p_arabic_v3.int8.onnx`, sherpa-onnx 1.13.8 on the Linux JVM, `numThreads = 2`,
greedy search, endpointing off. Measured by `SpikeMeasurementTest` in `:tracker`.

## Timestamp granularity

- Onsets lie on a 0.04 s grid: all 125 gaps between consecutive token onsets in `full_s1.wav`
  (50.4 s, Husary Al-Fatiha) are multiples of 0.04 s (one frame after 4x subsampling of 10 ms
  fbank frames).
- Gaps run from 0.08 s to 2.60 s; the median is 0.20 s. The largest gaps are pauses between ayahs.
- Tokens are CTC spikes, not segment starts. Fitting the onsets of ayah 1:1 at tempo 1.0 and 0.8 to
  `onset = lag + (original - lag) / tempo` gives a lag of about 0.12 s between the sound in the file
  and the reported onset.
- `ysProbs` is empty for CTC greedy search, so no token probability exists; `SherpaPhonemeSource`
  reports -1.

## Latency per 0.48 s chunk

- 505 calls of `accept()` with 100 ms blocks; 104 of them decoded a chunk. A decoding call took
  17.6 ms on average (maximum 20.6 ms) on this CPU with 2 threads.
- Decoding 50.4 s of audio took 1.9 s in total, 26 times faster than real time. The model adds the
  chunk wait itself: a token is reported at the earliest when the chunk containing it (0.48 s) is
  complete, so word latency is the chunk size plus about 20 ms plus the time the reciter takes to
  finish the phoneme.
- Not measured: a phone CPU (postponed phone check in docs/plan.md).

## Madd estimate from onset gaps

Onset gaps for the long vowels of Husary 1:1 (seconds from the vowel token's onset to the next
token's onset), original against the time-stretched copies:

| vowel token | tempo 0.8 | tempo 1.0 | tempo 1.2 |
| --- | --- | --- | --- |
| first natural madd (`اا`, 2 counts) | 0.20 | 0.20 | 0.12 |
| second natural madd (`اا`, 2 counts) | 0.16 | 0.16 | 0.16 |
| final madd (`ۦۦۦۦ`, 4 counts) | 0.36 | 0.24 | 0.48 (audio ends, not a gap) |
| gap before the final madd token | 1.48 | 1.24 | 0.80 |

- The gap after a long-vowel token does not follow the stretch: it is 0.16 to 0.20 s at all three
  speeds for natural madd, 5 to 6 frames, so single gaps are within one or two frames of noise.
- For the final madd, the elongation sits in the gap before the madd token (1.24 s, scaling with the
  tempo as 1.48 / 1.24 / 0.80), because the model emits the long-vowel token when the hold ends.
- Slowing one natural madd to 2 times (3 times) its length with ffmpeg raised the measured gap
  above the tolerance in 4 (5) of 10 cases, with 0 of 16 unmodified madds flagged. The gate of 9
  of 10 is missed by 4 or 5. Uniform time stretching cannot test the check, because it scales the
  short vowels that set the reference by the same factor.
- Conclusion: usable to see a long vowel held about 2 to 3 times too long, not for judging a
  difference of one count. A better measure is the span from the previous token's onset to the next
  token's onset; per-frame posteriors (forced alignment) would be exact.
