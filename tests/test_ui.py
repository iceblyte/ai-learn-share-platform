"""
AI 个性化学习资源分享平台 - UI 自动化测试
基于 Python + Playwright

运行方式:
    pip install playwright pytest-playwright
    playwright install chromium
    pytest tests/test_ui.py -v
"""

import pytest
from playwright.sync_api import Page, expect

BASE_URL = "http://localhost:5173"


@pytest.fixture(scope="session")
def browser_context_args():
    return {
        "viewport": {"width": 1280, "height": 720},
    }


class TestHomePage:
    """首页测试"""

    def test_home_page_loads(self, page: Page):
        """首页正常加载"""
        page.goto(BASE_URL)
        expect(page).to_have_title("AI 个性化学习资源分享平台")

    def test_hero_section_visible(self, page: Page):
        """Hero 区域可见"""
        page.goto(BASE_URL)
        expect(page.locator("h1")).to_contain_text("AI 个性化学习资源平台")

    def test_category_section_visible(self, page: Page):
        """分类区域可见"""
        page.goto(BASE_URL)
        expect(page.locator("text=资源分类")).to_be_visible()

    def test_hot_resources_visible(self, page: Page):
        """热门资源区域可见"""
        page.goto(BASE_URL)
        expect(page.locator("text=热门资源")).to_be_visible()

    def test_navigation_links(self, page: Page):
        """导航链接正常"""
        page.goto(BASE_URL)
        expect(page.locator("text=首页")).to_be_visible()


class TestLogin:
    """登录页面测试"""

    def test_login_page_loads(self, page: Page):
        """登录页正常加载"""
        page.goto(f"{BASE_URL}/login")
        expect(page.locator("h1")).to_contain_text("登录")

    def test_login_form_fields(self, page: Page):
        """登录表单字段存在"""
        page.goto(f"{BASE_URL}/login")
        expect(page.locator("input[placeholder='请输入用户名']")).to_be_visible()
        expect(page.locator("input[placeholder='请输入密码']")).to_be_visible()
        expect(page.locator("button[type='submit']")).to_be_visible()

    def test_login_success(self, page: Page):
        """登录成功跳转"""
        page.goto(f"{BASE_URL}/login")
        page.fill("input[placeholder='请输入用户名']", "admin")
        page.fill("input[placeholder='请输入密码']", "admin123")
        page.click("button[type='submit']")
        page.wait_for_url(f"{BASE_URL}/", timeout=5000)

    def test_login_failure(self, page: Page):
        """登录失败提示"""
        page.goto(f"{BASE_URL}/login")
        page.fill("input[placeholder='请输入用户名']", "admin")
        page.fill("input[placeholder='请输入密码']", "wrongpassword")
        page.click("button[type='submit']")
        expect(page.locator("text=用户名或密码错误")).to_be_visible(timeout=5000)

    def test_register_link(self, page: Page):
        """注册链接跳转"""
        page.goto(f"{BASE_URL}/login")
        page.click("text=立即注册")
        expect(page).to_have_url(f"{BASE_URL}/register")


class TestRegister:
    """注册页面测试"""

    def test_register_page_loads(self, page: Page):
        """注册页正常加载"""
        page.goto(f"{BASE_URL}/register")
        expect(page.locator("h1")).to_contain_text("注册")

    def test_password_mismatch(self, page: Page):
        """密码不一致提示"""
        page.goto(f"{BASE_URL}/register")
        page.fill("input[placeholder='3-20位字符']", "testuser_ui")
        page.fill("input[placeholder='your@email.com']", "ui@test.com")
        page.fill("input[placeholder='6-20位']", "password123")
        page.fill("input[placeholder='再次输入密码']", "different123")
        page.click("button[type='submit']")
        expect(page.locator("text=两次输入的密码不一致")).to_be_visible()


class TestSearch:
    """搜索页面测试"""

    def test_search_page_loads(self, page: Page):
        """搜索页正常加载"""
        page.goto(f"{BASE_URL}/search")
        expect(page.locator("text=关键词搜索")).to_be_visible()

    def test_nl_search_toggle(self, page: Page):
        """自然语言搜索切换"""
        page.goto(f"{BASE_URL}/search")
        page.click("text=AI 自然语言搜索")
        expect(page.locator("input[placeholder*='推荐']")).to_be_visible()

    def test_search_results(self, page: Page):
        """搜索返回结果"""
        page.goto(f"{BASE_URL}/search")
        page.fill("input", "Java")
        page.click("text=搜索")
        # Wait for results
        page.wait_for_timeout(2000)


class TestNavigation:
    """导航测试"""

    def test_header_visible(self, page: Page):
        """头部导航可见"""
        page.goto(BASE_URL)
        expect(page.locator("header")).to_be_visible()

    def test_search_bar_in_header(self, page: Page):
        """头部搜索栏可见"""
        page.goto(BASE_URL)
        expect(page.locator("header input[placeholder*='搜索']")).to_be_visible()

    def test_login_register_buttons(self, page: Page):
        """未登录显示登录/注册按钮"""
        page.goto(BASE_URL)
        expect(page.locator("text=登录")).to_be_visible()
        expect(page.locator("text=注册")).to_be_visible()

    def test_footer_visible(self, page: Page):
        """底部导航可见"""
        page.goto(BASE_URL)
        expect(page.locator("footer")).to_be_visible()


class TestResponsive:
    """响应式测试"""

    def test_mobile_viewport(self, page: Page):
        """移动端视口正常"""
        page.set_viewport_size({"width": 375, "height": 812})
        page.goto(BASE_URL)
        expect(page.locator("h1")).to_be_visible()
