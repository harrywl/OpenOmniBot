import 'package:flutter/material.dart';
import 'package:ui/services/assists_core_service.dart';

/// 权限按钮卡片
///
/// 用于显示未授权应用的权限开启按钮
class PermissionButtonCard extends StatelessWidget {
  final Map<String, dynamic> cardData;

  const PermissionButtonCard({super.key, required this.cardData});

  void _onTap() {
    AssistsMessageService.navigateToMainEngineRoute('/home/companion_setting');
  }


  @override
  Widget build(BuildContext context) {
    final buttonText = cardData['buttonText'] as String? ?? '去开启权限';

    return GestureDetector(
      onTap: _onTap,
      child: Container(
        width: 295,
        height: 40,
        decoration: ShapeDecoration(
          color: const Color(0xFF333333),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
        ),
        child: Center(
          child: Text(
            buttonText,
            textAlign: TextAlign.center,
            style: const TextStyle(
              color: Colors.white,
              fontSize: 14,
              fontFamily: 'PingFang SC',
              fontWeight: FontWeight.w600,
              height: 1.43,
              letterSpacing: 0.44,
            ),
          ),
        ),
      ),
    );
  }
}

