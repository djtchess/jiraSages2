import { routes } from './app.routes';

describe('App routes', () => {
  it('should define a root route', () => {
    const rootRoute = routes.find((route) => route.path === '');

    expect(rootRoute).toBeDefined();
    expect(rootRoute?.loadComponent).toBeDefined();
  });

  it('should define a wildcard route to 404 component', () => {
    const wildcardRoute = routes.find((route) => route.path === '**');

    expect(wildcardRoute).toBeDefined();
    expect(wildcardRoute?.component).toBeDefined();
  });
});
