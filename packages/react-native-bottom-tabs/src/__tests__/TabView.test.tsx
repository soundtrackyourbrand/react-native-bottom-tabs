import { act, create, type ReactTestRenderer } from 'react-test-renderer';
import TabView from '../TabView';

const navigationState = {
  index: 0,
  routes: [
    { key: 'home', title: 'Home' },
    { key: 'search', title: 'Search' },
  ],
};

type TabViewProps = React.ComponentProps<typeof TabView>;

function renderTabView(props: Partial<TabViewProps> = {}): ReactTestRenderer {
  let renderer!: ReactTestRenderer;
  act(() => {
    renderer = create(
      <TabView
        navigationState={navigationState}
        renderScene={() => null}
        onIndexChange={() => {}}
        {...props}
      />
    );
  });
  return renderer;
}

function nativeTabViewProps(renderer: ReactTestRenderer) {
  return renderer.root.findByType('RNCTabView' as never).props;
}

describe('TabView', () => {
  describe('tabBarHideOnKeyboard', () => {
    it('is left unset by default', () => {
      expect(nativeTabViewProps(renderTabView()).tabBarHideOnKeyboard).toBe(
        undefined
      );
    });

    it('is forwarded to the native view', () => {
      const renderer = renderTabView({ tabBarHideOnKeyboard: true });
      expect(nativeTabViewProps(renderer).tabBarHideOnKeyboard).toBe(true);
    });

    it('does not affect tabBarHidden', () => {
      const renderer = renderTabView({
        tabBarHideOnKeyboard: true,
        tabBarHidden: false,
      });
      expect(nativeTabViewProps(renderer).tabBarHidden).toBe(false);
    });
  });
});
