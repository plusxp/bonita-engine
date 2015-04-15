/**
 * Copyright (C) 2015 BonitaSoft S.A.
 * BonitaSoft, 32 rue Gustave Eiffel - 38000 Grenoble
 * This library is free software; you can redistribute it and/or modify it under the terms
 * of the GNU Lesser General Public License as published by the Free Software Foundation
 * version 2.1 of the License.
 * This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License along with this
 * program; if not, write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth
 * Floor, Boston, MA 02110-1301, USA.
 **/
package org.bonitasoft.engine.business.application.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.bonitasoft.engine.api.ImportError;
import org.bonitasoft.engine.api.ImportStatus;
import org.bonitasoft.engine.business.application.ApplicationService;
import org.bonitasoft.engine.business.application.importer.ImportResult;
import org.bonitasoft.engine.business.application.model.SApplication;
import org.bonitasoft.engine.business.application.model.SApplicationPage;
import org.bonitasoft.engine.business.application.model.impl.SApplicationImpl;
import org.bonitasoft.engine.business.application.xml.ApplicationMenuNode;
import org.bonitasoft.engine.business.application.xml.ApplicationNode;
import org.bonitasoft.engine.business.application.xml.ApplicationPageNode;
import org.bonitasoft.engine.commons.exceptions.SObjectNotFoundException;
import org.bonitasoft.engine.exception.ExportException;
import org.bonitasoft.engine.exception.ImportException;
import org.bonitasoft.engine.page.PageService;
import org.bonitasoft.engine.page.SPage;
import org.bonitasoft.engine.persistence.QueryOptions;
import org.bonitasoft.engine.persistence.SBonitaReadException;
import org.bonitasoft.engine.profile.ProfileService;
import org.bonitasoft.engine.profile.exception.profile.SProfileNotFoundException;
import org.bonitasoft.engine.profile.model.SProfile;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ApplicationNodeConverterTest {

    @Mock
    private ProfileService profileService;

    @Mock
    private ApplicationService applicationService;

    @Mock
    private ApplicationPageNodeConverter pageConverter;

    @Mock
    private ApplicationMenuNodeConverter menuConverter;

    @Mock
    private PageService pageService;

    @Mock
    private SPage defaultLayout;

    public static final long DEFAULT_LAYOUT_ID = 101;

    @InjectMocks
    private ApplicationNodeConverter converter;

    @Before
    public void setUp() throws Exception {
        given(defaultLayout.getId()).willReturn(DEFAULT_LAYOUT_ID);
        given(defaultLayout.getName()).willReturn(ApplicationService.DEFAULT_LAYOUT_NAME);
        given(pageService.getPageByName(ApplicationService.DEFAULT_LAYOUT_NAME)).willReturn(defaultLayout);
    }

    @Test
    public void toNode_should_return_convert_all_string_fields() throws Exception {
        //given
        long createdBy = 11L;
        final SApplicationImpl application = new SApplicationImpl("app", "my app", "1.0", new Date().getTime(), createdBy, "enabled", null, null);
        application.setDescription("this is my app");
        application.setIconPath("/icon.jpg");

        //when
        final ApplicationNode applicationNode = converter.toNode(application);

        //then
        assertThat(applicationNode).isNotNull();
        assertThat(applicationNode.getToken()).isEqualTo("app");
        assertThat(applicationNode.getDisplayName()).isEqualTo("my app");
        assertThat(applicationNode.getVersion()).isEqualTo("1.0");
        assertThat(applicationNode.getDescription()).isEqualTo("this is my app");
        assertThat(applicationNode.getIconPath()).isEqualTo("/icon.jpg");
        assertThat(applicationNode.getState()).isEqualTo("enabled");
        assertThat(applicationNode.getProfile()).isNull();
        assertThat(applicationNode.getHomePage()).isNull();

    }

    @Test
    public void toNode_should_replace_profile_id_by_profile_name() throws Exception {
        //given
        final SApplicationImpl application = new SApplicationImpl("app", "my app", "1.0", new Date().getTime(), 10, "enabled", null, null);
        application.setProfileId(7L);
        final SProfile profile = mock(SProfile.class);
        given(profile.getName()).willReturn("admin");

        given(profileService.getProfile(7L)).willReturn(profile);

        //when
        final ApplicationNode applicationNode = converter.toNode(application);

        //then
        assertThat(applicationNode).isNotNull();
        assertThat(applicationNode.getProfile()).isEqualTo("admin");
    }

    @Test(expected = ExportException.class)
    public void toNode_should_throw_ExportException_when_profileService_throws_exception() throws Exception {
        //given
        final SApplicationImpl application = new SApplicationImpl("app", "my app", "1.0", new Date().getTime(), 10L, "enabled", null, null);
        application.setProfileId(7L);

        given(profileService.getProfile(7L)).willThrow(new SProfileNotFoundException(""));

        //when
        converter.toNode(application);

        //then exception
    }

    @Test
    public void toNode_should_replaceHomePageId_by_application_page_token() throws Exception {
        //given
        final SApplicationImpl application = new SApplicationImpl("app", "my app", "1.0", new Date().getTime(), 10L, "enabled", null, null);
        application.setHomePageId(8L);
        final SApplicationPage homePage = mock(SApplicationPage.class);
        given(homePage.getToken()).willReturn("home");

        given(applicationService.getApplicationPage(8L)).willReturn(homePage);

        //when
        final ApplicationNode applicationNode = converter.toNode(application);

        //then
        assertThat(applicationNode).isNotNull();
        assertThat(applicationNode.getHomePage()).isEqualTo("home");
    }

    @Test(expected = ExportException.class)
    public void toNode_should_throw_ExportException_when_applicationService_throws_exception() throws Exception {
        //given
        final SApplicationImpl application = new SApplicationImpl("app", "my app", "1.0", new Date().getTime(), 10L, "enabled", null, null);
        application.setHomePageId(8L);

        final SApplicationPage homePage = mock(SApplicationPage.class);
        given(homePage.getToken()).willReturn("home");

        given(applicationService.getApplicationPage(8L)).willThrow(new SObjectNotFoundException());

        //when
        converter.toNode(application);

        //then exception
    }

    @Test
    public void toNode_should_replaceLayoutId_by_page_name() throws Exception {
        //given
        final SApplicationImpl application = new SApplicationImpl("app", "my app", "1.0", new Date().getTime(), 10L, "enabled", 9L, null);
        final SPage layout = mock(SPage.class);
        given(layout.getName()).willReturn("mainLayout");

        given(pageService.getPage(9L)).willReturn(layout);

        //when
        final ApplicationNode applicationNode = converter.toNode(application);

        //then
        assertThat(applicationNode).isNotNull();
        assertThat(applicationNode.getLayout()).isEqualTo("mainLayout");
    }

    @Test
    public void toNode_should_replaceThemeId_by_page_name() throws Exception {
        //given
        long themeId = 20L;
        final SApplicationImpl application = new SApplicationImpl("app", "my app", "1.0", new Date().getTime(), 10L, "enabled", null, themeId);
        final SPage layout = mock(SPage.class);
        given(layout.getName()).willReturn("mainTheme");

        given(pageService.getPage(themeId)).willReturn(layout);

        //when
        final ApplicationNode applicationNode = converter.toNode(application);

        //then
        assertThat(applicationNode).isNotNull();
        assertThat(applicationNode.getTheme()).isEqualTo("mainTheme");
    }

    @Test(expected = ExportException.class)
    public void toNodeShouldThrowExceptionAtMenuConversion() throws Exception {
        //given
        final SApplicationImpl application = new SApplicationImpl("app", "my app", "1.0", new Date().getTime(), 10L, "enabled", null, null);

        doThrow(new SBonitaReadException("")).when(menuConverter).addMenusToApplicationNode(anyLong(), anyLong(), any(ApplicationNode.class),
                any(ApplicationMenuNode.class));

        converter.toNode(application);
    }

    @Test
    public void toNodeShouldAddConvertedMenus() throws Exception {
        //given
        final long applicationId = 1191L;
        final SApplicationImpl application = new SApplicationImpl("app", "my app", "1.0", new Date().getTime(), 10L, "enabled", null, null);
        application.setId(applicationId);

        doNothing().when(menuConverter).addMenusToApplicationNode(eq(applicationId), isNull(Long.class), any(ApplicationNode.class),
                isNull(ApplicationMenuNode.class));

        //when
        converter.toNode(application);

        //then
        verify(menuConverter).addMenusToApplicationNode(eq(applicationId), isNull(Long.class), any(ApplicationNode.class), isNull(ApplicationMenuNode.class));
    }

    @Test(expected = ExportException.class)
    public void toNodeShouldAddThrowExceptionAtPageConversion() throws Exception {
        //given
        final SApplicationImpl application = new SApplicationImpl("app", "my app", "1.0", new Date().getTime(), 10L, "enabled", null, null);

        given(applicationService.searchApplicationPages(any(QueryOptions.class))).willThrow(new SBonitaReadException(""));

        converter.toNode(application);
    }

    @Test
    public void toNodeShouldAddConvertedPages() throws Exception {
        //given
        final SApplicationImpl application = new SApplicationImpl("app", "my app", "1.0", new Date().getTime(), 10L, "enabled", null, null);
        final List<SApplicationPage> pages = new ArrayList<>(1);
        final SApplicationPage page = mock(SApplicationPage.class);
        pages.add(page);

        final ApplicationPageNode pageNode = mock(ApplicationPageNode.class);
        given(pageConverter.toPage(page)).willReturn(pageNode);

        given(applicationService.searchApplicationPages(any(QueryOptions.class))).willReturn(pages).willReturn(Collections.<SApplicationPage> emptyList());

        //when
        final ApplicationNode applicationNode = converter.toNode(application);

        //then
        assertThat(applicationNode.getApplicationPages().size()).isEqualTo(1);
        assertThat(applicationNode.getApplicationPages().get(0)).isEqualTo(pageNode);
    }

    @Test
    public void toSApplication_should_return_ImportResult_with_no_errors_and_application_with_all_fields_except_home_page() throws Exception {
        //given
        final ApplicationNode node = new ApplicationNode();
        node.setDisplayName("My app");
        node.setDescription("This is my app");
        node.setHomePage("home");
        node.setVersion("1.0");
        node.setToken("app");
        node.setIconPath("/icon.jpg");
        node.setProfile("admin");
        node.setState("ENABLED");

        long profileId = 8L;
        final SProfile profile = mock(SProfile.class);
        given(profile.getId()).willReturn(profileId);
        given(profileService.getProfileByName("admin")).willReturn(profile);

        //when
        long createdBy = 1L;
        final ImportResult importResult = converter.toSApplication(node, createdBy);

        //then
        assertThat(importResult).isNotNull();

        final SApplication application = importResult.getApplication();
        assertThat(application.getDisplayName()).isEqualTo("My app");
        assertThat(application.getDescription()).isEqualTo("This is my app");
        assertThat(application.getHomePageId()).isNull();
        assertThat(application.getVersion()).isEqualTo("1.0");
        assertThat(application.getToken()).isEqualTo("app");
        assertThat(application.getIconPath()).isEqualTo("/icon.jpg");
        assertThat(application.getProfileId()).isEqualTo(profileId);
        assertThat(application.getState()).isEqualTo("ENABLED");
        assertThat(application.getCreatedBy()).isEqualTo(createdBy);

        final ImportStatus importStatus = importResult.getImportStatus();
        assertThat(importStatus.getName()).isEqualTo("app");
        assertThat(importStatus.getStatus()).isEqualTo(ImportStatus.Status.ADDED);
        assertThat(importStatus.getErrors()).isEmpty();

    }

    @Test
    public void toSApplication_should_always_use_default_layout() throws Exception {
        //given
        final ApplicationNode node = new ApplicationNode();
        node.setToken("app");
        node.setLayout("dummyLayout"); // will not be used, the layout will be always the default one

        given(pageService.getPageByName(ApplicationService.DEFAULT_LAYOUT_NAME)).willReturn(defaultLayout);

        //when
        long createdBy = 1L;
        final ImportResult importResult = converter.toSApplication(node, createdBy);

        //then
        assertThat(importResult).isNotNull();

        final SApplication application = importResult.getApplication();
        assertThat(application.getLayoutId()).isEqualTo(DEFAULT_LAYOUT_ID);

        final ImportStatus importStatus = importResult.getImportStatus();
        assertThat(importStatus.getName()).isEqualTo("app");
        assertThat(importStatus.getStatus()).isEqualTo(ImportStatus.Status.ADDED);
        assertThat(importStatus.getErrors()).isEmpty();

    }

    @Test
    public void toSApplication_should_return_application_with_null_profile_id_when_node_has_no_profile() throws Exception {
        //given
        final ApplicationNode node = new ApplicationNode();
        node.setProfile(null);

        //when
        final ImportResult importResult = converter.toSApplication(node, 1L);

        //then
        assertThat(importResult).isNotNull();
        assertThat(importResult.getApplication().getProfileId()).isNull();
    }

    @Test
    public void toSApplication_should_return_Import_result_with_errors_when_profile_is_not_found() throws Exception {
        //given
        final ApplicationNode node = new ApplicationNode();
        node.setProfile("admin");
        node.setVersion("1.0");
        node.setToken("app");
        node.setState("ENABLED");

        given(profileService.getProfileByName("admin")).willThrow(new SProfileNotFoundException(""));

        //when
        final ImportResult importResult = converter.toSApplication(node, 1L);

        //then
        assertThat(importResult.getApplication().getProfileId()).isNull();

        final ImportStatus importStatus = importResult.getImportStatus();
        assertThat(importStatus.getName()).isEqualTo("app");
        assertThat(importStatus.getStatus()).isEqualTo(ImportStatus.Status.ADDED);
        assertThat(importStatus.getErrors()).containsExactly(new ImportError("admin", ImportError.Type.PROFILE));
    }

    @Test(expected = ImportException.class)
    public void toSApplication_should_throw_ImportException_when_layout_is_not_found() throws Exception {
        //given
        final ApplicationNode node = new ApplicationNode();
        node.setVersion("1.0");
        node.setToken("app");
        node.setState("ENABLED");

        given(pageService.getPageByName(ApplicationService.DEFAULT_LAYOUT_NAME)).willReturn(null);

        //when
        converter.toSApplication(node, 1L);

        //then exception
    }


}
