import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:ui/models/task_models.dart';

class TaskStorageService {
  static const String _tasksKey = 'stored_tasks';
  
  // 保存任务列表
  static Future<bool> saveTasks(List<TaskData> tasks) async {
    final prefs = await SharedPreferences.getInstance();
    final List<String> jsonTasks = tasks.map(
      (task) => jsonEncode(task.toJson())
    ).toList();
    
    return await prefs.setStringList(_tasksKey, jsonTasks);
  }
  
  // 加载任务列表
  static Future<List<TaskData>> loadTasks() async {
    final prefs = await SharedPreferences.getInstance();
    final List<String>? jsonTasks = prefs.getStringList(_tasksKey);
    
    if (jsonTasks == null || jsonTasks.isEmpty) {
      return [];
    }
    
    return jsonTasks.map((jsonStr) {
      final Map<String, dynamic> json = jsonDecode(jsonStr);
      return TaskData.fromJson(json);
    }).toList();
  }
  
  // 添加或更新任务
  static Future<bool> saveTask(TaskData task) async {
    final tasks = await loadTasks();
    final existingIndex = tasks.indexWhere((t) => t.id == task.id);
    
    if (existingIndex != -1) {
      tasks[existingIndex] = task;
    } else {
      tasks.add(task);
    }
    
    return await saveTasks(tasks);
  }
  
  // 删除任务
  static Future<bool> deleteTask(String taskId) async {
    final tasks = await loadTasks();
    tasks.removeWhere((task) => task.id == taskId);
    return await saveTasks(tasks);
  }
  
  // 清空所有任务
  static Future<bool> clearTasks() async {
    final prefs = await SharedPreferences.getInstance();
    return await prefs.remove(_tasksKey);
  }

  // 根据ID获取任务
  static Future<TaskData?> getTaskById(String? taskId) async {
    if (taskId == null) return null;
    final tasks = await loadTasks();
    for (final task in tasks) {
      if (task.id == taskId) {
        return task;
      }
    }
    return null;
  }
}
