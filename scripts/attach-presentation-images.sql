-- S3 업로드를 완료한 presentation-images의 객체를 콘텐츠 대표 이미지로 직접 연결합니다.
-- 전제: scripts/presentation-image-manifest.json의 objectKey에 해당하는 PNG 19개가 S3에 있고,
--       기존 프레젠테이션 시드 데이터가 local_stamp에 존재해야 합니다.

USE local_stamp;

SET NAMES utf8mb4;
SET time_zone = '+00:00';
START TRANSACTION;

SET @attached_at = UTC_TIMESTAMP(6);

INSERT INTO image_object (
    object_key, media_type, byte_size, checksum, lifecycle_status,
    delete_attempt_count, last_delete_attempted_at, created_at,
    created_by_user_id, region_id, upload_expires_at, linked_at
)
SELECT
    mapping.object_key,
    'image/png',
    mapping.byte_size,
    mapping.checksum,
    'ACTIVE',
    0,
    NULL,
    @attached_at,
    NULL,
    region.region_id,
    @attached_at,
    @attached_at
FROM (
    SELECT 'GIMHAE' AS region_code, 'contents/presentation/2026/08/21/172c0d77-0154-4029-bd38-16afe32a7cf8.png' AS object_key, 2178428 AS byte_size, 'JR7MMWav040MRb49+NMYPyDIQT0Gr+278qg158eRRi0=' AS checksum
    UNION ALL SELECT 'GIMHAE', 'contents/presentation/2026/08/21/8e6df560-b932-48a9-b8d8-d45e09daf911.png', 1844975, 'kKomGGYdsf7xdbfnNgNcHXcXSw9hlWi5PqSa+v+5n6o='
    UNION ALL SELECT 'GIMHAE', 'contents/presentation/2026/08/21/03b98105-24cb-48fa-a7cd-37a308fd4e3c.png', 2478895, 'N80nvS2Xr986nnVAkzhkyosbphEbVrNHU1cXh7PK6/E='
    UNION ALL SELECT 'GIMHAE', 'contents/presentation/2026/08/21/0ec08948-2ede-46e3-9205-28dce19eb1b2.png', 1883029, '47x+j1CEhwg+ygiSQemc6LpIir66X+aIw9+sb5nCbWQ='
    UNION ALL SELECT 'GIMHAE', 'contents/presentation/2026/08/21/6a917a20-c997-440c-9281-782415939ba1.png', 2085666, 'z34AJ6z1NuEDS2SZJcbntXb6IDAPIt5JIx1cP/rXEx4='
    UNION ALL SELECT 'GIMHAE', 'contents/presentation/2026/08/21/9c2c7640-7d5c-46d0-84ec-fea53c0aaf98.png', 2136943, '0Qbau97NUSSHOwMCyMEZKQ1mYaFHnDm6NYUKMfdoo5s='
    UNION ALL SELECT 'GIMHAE', 'contents/presentation/2026/08/21/e60c27dd-e641-46be-aae9-28382144240b.png', 2314913, '7FZqUsVpatuSPbJXbW0GvqPqwkaDwEs7Uv/oLIUDhes='
    UNION ALL SELECT 'GIMHAE', 'contents/presentation/2026/08/23/a3f2d104-720a-43cf-8c4d-10c60fa52ff2.png', 1793109, 'bodMOG5McIGIRle1kU3NsRq84Cc70Qx7lys26r6tRPo='
    UNION ALL SELECT 'GIMHAE', 'contents/presentation/2026/08/23/5c08eeca-ffb9-4c42-ba85-8bd69cc93c37.png', 2247860, '5OwGi9fhVvMiqxXrQ0m9FJIwMfxk4AKHVEM3R6Ni8GI='
    UNION ALL SELECT 'GIMHAE', 'contents/presentation/2026/08/23/3a57279e-121f-418b-8d85-f2941500114e.png', 1899166, 'GMiAorZY3FT9fHda/yglV5JSvB9CniDgXWPNDEBOXgw='
    UNION ALL SELECT 'DONGHAE', 'contents/presentation/2026/08/21/3745fbef-9714-4c65-9ee4-b50f461ed482.png', 2371701, 'fIfQyBWB2OAqp+FCW/uIiILDLxl0SqhchMn+JUSL9aA='
    UNION ALL SELECT 'DONGHAE', 'contents/presentation/2026/08/23/2ac3305d-3d37-437b-9ae3-48c75c9866e5.png', 2085457, 'HVs4eYr/PibWWZspww/ZjD0vsjRLcu1DI3VucPsKDkc='
    UNION ALL SELECT 'DONGHAE', 'contents/presentation/2026/08/23/7863cad0-2b33-4575-97a1-dda7ba5e1782.png', 2220160, 'G7aZUN+5k1YXY1X4VEHn/auaJ2FTFRsyiabg/ExzHPA='
    UNION ALL SELECT 'DONGHAE', 'contents/presentation/2026/08/21/2d474248-6dc7-418f-844e-e9938bfee75c.png', 1980853, 'DiJfPymGL3kA+F0Q0h1pKZ0XvUtdUpxYxIPWECsy/Nc='
    UNION ALL SELECT 'DONGHAE', 'contents/presentation/2026/08/21/bb41b300-a47d-4ba9-b06d-11c1d2f3d8be.png', 2455653, 'Up0b4AjoL/pDDJF64Z+3/1lVqeuUYc/tOwRT5lZsG6Q='
    UNION ALL SELECT 'DONGHAE', 'contents/presentation/2026/08/21/21815f27-ea0a-4422-b8fb-802a80458cbf.png', 2572605, 'B0rBq/cIrji0nj3KxceC/YmY02sP+xDpfYfEVloG4fY='
    UNION ALL SELECT 'DONGHAE', 'contents/presentation/2026/08/21/7fce0505-f618-4fbf-8729-75ad95ff5604.png', 1816981, 'bNBs9lK41UdK9QHFhGg+YRpfIYIuDEvoSSlAMdykJqw='
    UNION ALL SELECT 'DONGHAE', 'contents/presentation/2026/08/21/476a496a-8b93-4126-8b98-5eca5b441776.png', 2991081, 'SMGv+KyapaoFwi8s2Vfei/ttJ2vCv+XEfMCuFxI38dE='
    UNION ALL SELECT 'DONGHAE', 'contents/presentation/2026/08/21/7e6ee7ec-69cb-44f9-89b5-c8d1977bbc91.png', 2343503, 'dS+bWR//xiAfwpGPjZK2Vs9hWtS3jKvHfidFDljQbtE='
) AS mapping
JOIN region ON region.region_code = mapping.region_code;

UPDATE content
JOIN (
    SELECT '가야금과 함께 걷는 김해 역사 여행' AS title, 'contents/presentation/2026/08/21/172c0d77-0154-4029-bd38-16afe32a7cf8.png' AS object_key
    UNION ALL SELECT '김해 도예 체험', 'contents/presentation/2026/08/21/8e6df560-b932-48a9-b8d8-d45e09daf911.png'
    UNION ALL SELECT '김해 야간 산책', 'contents/presentation/2026/08/21/03b98105-24cb-48fa-a7cd-37a308fd4e3c.png'
    UNION ALL SELECT '대성동고분박물관 깊이 보기', 'contents/presentation/2026/08/21/0ec08948-2ede-46e3-9205-28dce19eb1b2.png'
    UNION ALL SELECT '낙동강 카약 일몰 체험', 'contents/presentation/2026/08/21/6a917a20-c997-440c-9281-782415939ba1.png'
    UNION ALL SELECT '봉리단길 베이킹 클래스', 'contents/presentation/2026/08/21/9c2c7640-7d5c-46d0-84ec-fea53c0aaf98.png'
    UNION ALL SELECT '화포천 습지 생태 탐방', 'contents/presentation/2026/08/21/e60c27dd-e641-46be-aae9-28382144240b.png'
    UNION ALL SELECT '김해 전통차 시음 체험', 'contents/presentation/2026/08/23/a3f2d104-720a-43cf-8c4d-10c60fa52ff2.png'
    UNION ALL SELECT '김해 가을 문화 해설', 'contents/presentation/2026/08/23/5c08eeca-ffb9-4c42-ba85-8bd69cc93c37.png'
    UNION ALL SELECT '김해 야외 음악 체험', 'contents/presentation/2026/08/23/3a57279e-121f-418b-8d85-f2941500114e.png'
    UNION ALL SELECT '묵호항 새벽 경매 체험', 'contents/presentation/2026/08/21/3745fbef-9714-4c65-9ee4-b50f461ed482.png'
    UNION ALL SELECT '동해 해안 생태 관찰', 'contents/presentation/2026/08/23/2ac3305d-3d37-437b-9ae3-48c75c9866e5.png'
    UNION ALL SELECT '동해 항구 드로잉 체험', 'contents/presentation/2026/08/23/7863cad0-2b33-4575-97a1-dda7ba5e1782.png'
    UNION ALL SELECT '동해 해변 요가', 'contents/presentation/2026/08/21/2d474248-6dc7-418f-844e-e9938bfee75c.png'
    UNION ALL SELECT '도째비골 스카이워크 해설', 'contents/presentation/2026/08/21/bb41b300-a47d-4ba9-b06d-11c1d2f3d8be.png'
    UNION ALL SELECT '무릉별유천지 암벽 체험', 'contents/presentation/2026/08/21/21815f27-ea0a-4422-b8fb-802a80458cbf.png'
    UNION ALL SELECT '망상 해변 서핑 입문', 'contents/presentation/2026/08/21/7fce0505-f618-4fbf-8729-75ad95ff5604.png'
    UNION ALL SELECT '천곡황금박쥐동굴 탐험', 'contents/presentation/2026/08/21/476a496a-8b93-4126-8b98-5eca5b441776.png'
    UNION ALL SELECT '논골담길 사진 산책', 'contents/presentation/2026/08/21/7e6ee7ec-69cb-44f9-89b5-c8d1977bbc91.png'
) AS mapping ON mapping.title = content.title
JOIN image_object ON image_object.object_key = mapping.object_key
    AND image_object.region_id = content.region_id
SET
    content.representative_image_object_id = image_object.image_object_id,
    content.representative_image_assigned_at = @attached_at
WHERE content.deleted_at IS NULL;

COMMIT;

SELECT
    region.region_code,
    content.content_id,
    content.title,
    image_object.object_key,
    image_object.media_type,
    image_object.byte_size,
    image_object.checksum
FROM content
JOIN region ON region.region_id = content.region_id
JOIN image_object ON image_object.image_object_id = content.representative_image_object_id
WHERE content.title IN (
    '가야금과 함께 걷는 김해 역사 여행', '김해 도예 체험', '김해 야간 산책',
    '대성동고분박물관 깊이 보기', '낙동강 카약 일몰 체험', '봉리단길 베이킹 클래스',
    '화포천 습지 생태 탐방', '김해 전통차 시음 체험', '김해 가을 문화 해설', '김해 야외 음악 체험',
    '묵호항 새벽 경매 체험', '동해 해안 생태 관찰', '동해 항구 드로잉 체험', '동해 해변 요가',
    '도째비골 스카이워크 해설', '무릉별유천지 암벽 체험', '망상 해변 서핑 입문',
    '천곡황금박쥐동굴 탐험', '논골담길 사진 산책'
)
ORDER BY content.content_id;
