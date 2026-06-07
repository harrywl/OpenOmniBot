import 'package:ui/models/favorite_record.dart';

class FavoriteCount {
  final FavoriteRecordType type;
  final int count;

  FavoriteCount({
    required this.type,
    required this.count,
  });

  factory FavoriteCount.fromMap(Map<dynamic, dynamic> map) {
    return FavoriteCount(
      type: FavoriteRecordTypeX.fromValue(map['type'] as String?),
      count: map['count'] ?? 0,
    );
  }

  Map<String, dynamic> toMap() {
    return {'type': type.value, 'count': count};
  }

  @override
  String toString() {
    return 'FavoriteCount(type: $type, count: $count)';
  }
}
