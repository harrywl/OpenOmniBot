class AppCount {
  final String appName;
  final int count;
  final String packageName;

  AppCount({
    required this.appName,
    required this.count,
    required this.packageName,
  });

  factory AppCount.fromMap(Map<dynamic, dynamic> map) {
    return AppCount(
      appName: map['appName'] ?? '',
      count: map['count'] ?? 0,
      packageName: map['packageName'] ?? '',
    );
  }

  Map<String, dynamic> toMap() {
    return {'appName': appName, 'count': count, 'packageName': packageName};
  }

  @override
  String toString() {
    return 'AppCount(appName: $appName, count: $count, packageName: $packageName)';
  }
}
