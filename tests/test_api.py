"""
AI 个性化学习资源分享平台 - API 接口自动化测试
基于 Python + Pytest + Requests

运行方式:
    pip install pytest requests
    pytest tests/test_api.py -v
"""

import requests
import pytest

BASE_URL = "http://localhost:8080/api/v1"


# ==================== Fixtures ====================

@pytest.fixture(scope="module")
def admin_token():
    """获取管理员 Token"""
    resp = requests.post(f"{BASE_URL}/auth/login", json={
        "username": "admin",
        "password": "admin123"
    })
    assert resp.status_code == 200
    return resp.json()["data"]["token"]


@pytest.fixture(scope="module")
def user_token():
    """获取普通用户 Token"""
    # 先注册
    requests.post(f"{BASE_URL}/auth/register", json={
        "username": "testuser_pytest",
        "email": "pytest@test.com",
        "password": "test123456"
    })
    # 再登录
    resp = requests.post(f"{BASE_URL}/auth/login", json={
        "username": "testuser_pytest",
        "password": "test123456"
    })
    assert resp.status_code == 200
    return resp.json()["data"]["token"]


@pytest.fixture(scope="module")
def publisher_token():
    """获取发布者 Token"""
    resp = requests.post(f"{BASE_URL}/auth/login", json={
        "username": "publisher",
        "password": "publisher123"
    })
    assert resp.status_code == 200
    return resp.json()["data"]["token"]


def auth_header(token):
    return {"Authorization": f"Bearer {token}"}


# ==================== 认证模块测试 ====================

class TestAuth:
    def test_register_success(self):
        """TC-AUTH-001: 正常注册"""
        resp = requests.post(f"{BASE_URL}/auth/register", json={
            "username": "newuser_test",
            "email": "newuser@test.com",
            "password": "password123",
            "nickname": "测试用户"
        })
        assert resp.status_code == 201
        data = resp.json()["data"]
        assert "token" in data
        assert data["username"] == "newuser_test"

    def test_register_duplicate_username(self):
        """TC-AUTH-002: 重复用户名注册"""
        resp = requests.post(f"{BASE_URL}/auth/register", json={
            "username": "admin",
            "email": "another@test.com",
            "password": "password123"
        })
        assert resp.status_code == 400

    def test_register_invalid_email(self):
        """TC-AUTH-003: 无效邮箱注册"""
        resp = requests.post(f"{BASE_URL}/auth/register", json={
            "username": "invalid_email_user",
            "email": "not-an-email",
            "password": "password123"
        })
        assert resp.status_code == 400

    def test_login_success(self):
        """TC-AUTH-005: 正常登录"""
        resp = requests.post(f"{BASE_URL}/auth/login", json={
            "username": "admin",
            "password": "admin123"
        })
        assert resp.status_code == 200
        data = resp.json()["data"]
        assert "token" in data
        assert data["role"] == "ADMIN"

    def test_login_wrong_password(self):
        """TC-AUTH-006: 错误密码登录"""
        resp = requests.post(f"{BASE_URL}/auth/login", json={
            "username": "admin",
            "password": "wrongpassword"
        })
        assert resp.status_code == 401

    def test_get_me(self, admin_token):
        """获取当前用户信息"""
        resp = requests.get(f"{BASE_URL}/auth/me", headers=auth_header(admin_token))
        assert resp.status_code == 200
        assert resp.json()["data"]["username"] == "admin"


# ==================== 资源模块测试 ====================

class TestResource:
    def test_list_resources(self):
        """TC-RES-001: 获取资源列表"""
        resp = requests.get(f"{BASE_URL}/resources")
        assert resp.status_code == 200
        data = resp.json()["data"]
        assert "records" in data
        assert "total" in data

    def test_list_with_category_filter(self):
        """TC-RES-002: 按分类筛选"""
        resp = requests.get(f"{BASE_URL}/resources", params={"categoryId": 1})
        assert resp.status_code == 200

    def test_list_with_keyword(self):
        """TC-RES-003: 按关键词搜索"""
        resp = requests.get(f"{BASE_URL}/resources", params={"keyword": "Java"})
        assert resp.status_code == 200

    def test_hot_resources(self):
        """获取热门资源"""
        resp = requests.get(f"{BASE_URL}/resources/hot")
        assert resp.status_code == 200

    def test_latest_resources(self):
        """获取最新资源"""
        resp = requests.get(f"{BASE_URL}/resources/latest")
        assert resp.status_code == 200

    def test_create_resource_as_publisher(self, publisher_token):
        """TC-RES-007: 发布者发布资源"""
        resp = requests.post(f"{BASE_URL}/resources",
                             json={
                                 "title": "Pytest 自动化测试资源",
                                 "categoryId": 1,
                                 "tags": ["Python", "测试"],
                                 "description": "这是一份由自动化测试创建的资源",
                                 "resourceType": "LINK",
                                 "externalUrl": "https://example.com"
                             },
                             headers=auth_header(publisher_token))
        assert resp.status_code == 201
        assert resp.json()["data"]["title"] == "Pytest 自动化测试资源"

    def test_create_resource_as_user(self, user_token):
        """TC-RES-008: 普通用户发布资源（应被拒绝）"""
        resp = requests.post(f"{BASE_URL}/resources",
                             json={
                                 "title": "Should Fail",
                                 "categoryId": 1,
                                 "description": "普通用户不应能发布",
                                 "resourceType": "LINK"
                             },
                             headers=auth_header(user_token))
        assert resp.status_code == 403

    def test_get_resource_detail(self):
        """TC-RES-005: 获取资源详情"""
        # 先获取列表拿到一个ID
        list_resp = requests.get(f"{BASE_URL}/resources", params={"size": 1})
        records = list_resp.json()["data"]["records"]
        if records:
            resource_id = records[0]["id"]
            resp = requests.get(f"{BASE_URL}/resources/{resource_id}")
            assert resp.status_code == 200
            assert resp.json()["data"]["id"] == resource_id

    def test_get_nonexistent_resource(self):
        """TC-RES-006: 获取不存在的资源"""
        resp = requests.get(f"{BASE_URL}/resources/99999")
        assert resp.status_code == 404


# ==================== 分类与标签测试 ====================

class TestCategoryAndTag:
    def test_get_category_tree(self):
        """获取分类树"""
        resp = requests.get(f"{BASE_URL}/categories")
        assert resp.status_code == 200
        categories = resp.json()["data"]
        assert len(categories) > 0

    def test_get_tags(self):
        """获取标签列表"""
        resp = requests.get(f"{BASE_URL}/tags")
        assert resp.status_code == 200

    def test_get_hot_tags(self):
        """获取热门标签"""
        resp = requests.get(f"{BASE_URL}/tags/hot")
        assert resp.status_code == 200

    def test_search_tags(self):
        """搜索标签"""
        resp = requests.get(f"{BASE_URL}/tags/search", params={"keyword": "Java"})
        assert resp.status_code == 200


# ==================== 互动模块测试 ====================

class TestInteraction:
    def test_like_resource(self, user_token):
        """TC-INT-001: 点赞资源"""
        # 获取一个资源ID
        list_resp = requests.get(f"{BASE_URL}/resources", params={"size": 1})
        records = list_resp.json()["data"]["records"]
        if records:
            resource_id = records[0]["id"]
            resp = requests.post(f"{BASE_URL}/resources/{resource_id}/like",
                                 headers=auth_header(user_token))
            assert resp.status_code == 200
            data = resp.json()["data"]
            assert "liked" in data
            assert "likeCount" in data

    def test_rate_resource(self, user_token):
        """TC-INT-004: 评分资源"""
        list_resp = requests.get(f"{BASE_URL}/resources", params={"size": 1})
        records = list_resp.json()["data"]["records"]
        if records:
            resource_id = records[0]["id"]
            resp = requests.post(f"{BASE_URL}/resources/{resource_id}/rating",
                                 json={"score": 5},
                                 headers=auth_header(user_token))
            assert resp.status_code == 200
            data = resp.json()["data"]
            assert data["myRating"] == 5

    def test_comment_resource(self, user_token):
        """TC-INT-006: 发表评论"""
        list_resp = requests.get(f"{BASE_URL}/resources", params={"size": 1})
        records = list_resp.json()["data"]["records"]
        if records:
            resource_id = records[0]["id"]
            resp = requests.post(f"{BASE_URL}/resources/{resource_id}/comments",
                                 json={"content": "自动化测试评论 - 这是一份好资源！"},
                                 headers=auth_header(user_token))
            assert resp.status_code == 201


# ==================== 搜索模块测试 ====================

class TestSearch:
    def test_keyword_search(self):
        """关键词搜索"""
        resp = requests.get(f"{BASE_URL}/search", params={"keyword": "Java"})
        assert resp.status_code == 200

    def test_hot_searches(self):
        """获取热门搜索"""
        resp = requests.get(f"{BASE_URL}/search/hot")
        assert resp.status_code == 200

    def test_nl_search(self, user_token):
        """TC-AI-002: 自然语言搜索"""
        resp = requests.post(f"{BASE_URL}/search/nl",
                             json={"query": "推荐关于Java并发编程且评分最高的前5个资源"},
                             headers=auth_header(user_token))
        assert resp.status_code == 200
        data = resp.json()["data"]
        assert "parsedIntent" in data
        assert "results" in data

    def test_ai_recommendations(self, user_token):
        """TC-AI-003: 个性化推荐分页列表"""
        resp = requests.get(f"{BASE_URL}/ai/recommendations",
                            params={"page": 1, "size": 10},
                            headers=auth_header(user_token))
        assert resp.status_code == 200
        data = resp.json()["data"]
        assert "records" in data
        assert "total" in data


# ==================== 管理员模块测试 ====================

class TestAdmin:
    def test_admin_statistics(self, admin_token):
        """TC-ADM-005: 查看平台统计"""
        resp = requests.get(f"{BASE_URL}/admin/statistics",
                            headers=auth_header(admin_token))
        assert resp.status_code == 200
        data = resp.json()["data"]
        assert "totalUsers" in data
        assert "totalResources" in data

    def test_admin_user_list(self, admin_token):
        """TC-ADM-001: 查看用户列表"""
        resp = requests.get(f"{BASE_URL}/admin/users",
                            headers=auth_header(admin_token))
        assert resp.status_code == 200

    def test_admin_access_denied(self, user_token):
        """TC-ADM-006: 普通用户访问管理接口"""
        resp = requests.get(f"{BASE_URL}/admin/users",
                            headers=auth_header(user_token))
        assert resp.status_code == 403


if __name__ == "__main__":
    pytest.main([__file__, "-v"])
